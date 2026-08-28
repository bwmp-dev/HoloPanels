package dev.bwmp.holopanels.render;

import dev.bwmp.holopanels.api.PanelEntry;
import dev.bwmp.holopanels.model.BoardDefinition;
import dev.bwmp.holopanels.model.ButtonDefinition;
import dev.bwmp.holopanels.model.PanelDefinition;
import dev.bwmp.holopanels.model.PanelLine;
import dev.bwmp.holopanels.model.PanelStyle;
import dev.bwmp.holopanels.model.ViewDefinition;
import dev.bwmp.holopanels.runtime.ConditionService;
import dev.bwmp.holopanels.runtime.ContentService;
import dev.bwmp.holopanels.runtime.ViewerSession;
import dev.bwmp.holopanels.text.TemplateService;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class LayoutService {
    /** A block is forty pixels of text at scale one, and a space is four of them. */
    private static final double PIXELS_PER_BLOCK = 40.0;
    private static final double SPACE_PIXELS = 4.0;
    /** How far the text sits in front of the backdrop it is drawn on. */
    private static final double TEXT_DEPTH = 0.01;

    private record RenderedLine(Component text, double scale) {
    }

    private record RenderBody(List<RenderedLine> lines, List<ClickRegion> regions) {
    }

    private final TemplateService templates;
    private final ConditionService conditions;
    private final ContentService content;

    public LayoutService(TemplateService templates, ConditionService conditions, ContentService content) {
        this.templates = templates;
        this.conditions = conditions;
        this.content = content;
    }

    public List<RenderedPanel> render(
            Player player,
            BoardDefinition board,
            ViewDefinition view,
            ViewerSession session,
            Location anchor
    ) {
        List<RenderedPanel> output = new ArrayList<>();
        for (PanelDefinition panel : view.panels().values()) {
            Optional<PanelEntry> selected = selected(panel, session);
            if (!conditions.test(panel.condition(), player, board, view, panel.id(), session, selected)) {
                continue;
            }
            RenderBody body = switch (panel.type()) {
                case LIST -> list(player, board, view, panel, session);
                case TEXT -> text(player, board, view, panel, session, selected);
                case BUTTONS -> buttons(player, board, view, panel, session, selected);
            };
            Location location = PanelGeometry.offset(anchor, panel.offset().right(), panel.offset().up(), panel.offset().forward());
            output.add(assemble(board, view, panel, location, body));
        }
        return output;
    }

    /**
     * Turns lines into the display entities that draw them.
     * <p>
     * Lines that agree on a scale share one display; a panel that declares a
     * size gets one more behind them all, drawing the box. Without that box
     * there is nothing to paint a background on but the text itself, which is
     * how a panel with no size still comes out as one display.
     */
    private RenderedPanel assemble(
            BoardDefinition board,
            ViewDefinition view,
            PanelDefinition panel,
            Location location,
            RenderBody body
    ) {
        PanelStyle style = panel.style();
        List<RenderedLine> lines = body.lines().isEmpty()
                ? List.of(new RenderedLine(Component.empty(), style.scale()))
                : body.lines();
        List<PanelStack.Run> runs = PanelStack.runs(lines.stream().map(RenderedLine::scale).toList(), style.lineHeight());
        double height = style.height(PanelStack.height(runs));
        double width = style.width(style.interactionWidth());
        boolean backdrop = style.size().isPresent();

        List<PanelLayer> layers = new ArrayList<>();
        if (backdrop) {
            int spaces = spaces(width);
            // Its own wrap width, or a box wider than the panel's line-width
            // would be folded in half by the client and come out the wrong size.
            layers.add(new PanelLayer(location, box(style, spaces, height), 1.0,
                    Math.max(style.lineWidth(), (int) Math.ceil(spaces * SPACE_PIXELS) + 8), true));
        }
        Vector normal = PanelGeometry.normalFor(location);
        for (PanelStack.Run run : runs) {
            Location at = location.clone().add(0.0, height - run.fromTop() - run.height(), 0.0);
            if (backdrop) {
                at.add(normal.clone().multiply(TEXT_DEPTH));
            }
            layers.add(new PanelLayer(at, join(lines.subList(run.firstLine(), run.lastLine() + 1)),
                    run.scale(), style.lineWidth(), !backdrop));
        }

        return new RenderedPanel(board.id(), view.id(), panel.id(), location, width, height, style,
                layers, PanelStack.bands(runs, style.lineHeight()), body.regions());
    }

    /**
     * The box, written as text, because the text inside a display is the only
     * thing that sizes its background: spaces for the width and empty lines for
     * the height. Spaces draw nothing, so the box is only ever its background.
     */
    private Component box(PanelStyle style, int spaces, double height) {
        int rows = Math.max(1, (int) Math.round(height / style.lineHeight()));
        Component text = Component.text(" ".repeat(spaces));
        for (int row = 1; row < rows; row++) {
            text = text.append(Component.newline());
        }
        return text;
    }

    private RenderBody list(
            Player player,
            BoardDefinition board,
            ViewDefinition view,
            PanelDefinition panel,
            ViewerSession session
    ) {
        List<PanelEntry> entries = content.entries(player, board, view, panel, session);
        session.selection(panel.id()).filter(selected -> entries.stream().noneMatch(entry -> entry.id().equals(selected)))
                .ifPresent(ignored -> session.clearSelection(panel.id()));

        List<RenderedLine> lines = new ArrayList<>();
        for (String header : panel.header()) {
            lines.add(plain(templates.render(header, player, board.id(), view.id(), Optional.empty(), session.state()), panel));
        }
        List<ClickRegion> regions = new ArrayList<>();
        if (entries.isEmpty()) {
            lines.add(plain(templates.render(panel.emptyText(), player, board.id(), view.id(), Optional.empty(), session.state()), panel));
            return new RenderBody(lines, regions);
        }

        int maxPage = Math.max(0, (entries.size() - 1) / panel.pageSize());
        int page = Math.min(session.page(panel.id()), maxPage);
        if (page != session.page(panel.id())) {
            session.page(panel.id(), page);
        }
        int start = page * panel.pageSize();
        int end = Math.min(entries.size(), start + panel.pageSize());
        for (int index = start; index < end; index++) {
            PanelEntry entry = entries.get(index);
            if (entry.heading()) {
                lines.add(plain(templates.render(panel.headingRowTemplate(),
                        player, board.id(), view.id(), Optional.of(entry), session.state()), panel));
                continue;
            }
            boolean selected = session.selection(panel.id()).map(entry.id()::equals).orElse(false);
            int line = lines.size();
            lines.add(plain(templates.render(selected ? panel.selectedRowTemplate() : panel.rowTemplate(),
                    player, board.id(), view.id(), Optional.of(entry), session.state()), panel));
            regions.add(new ClickRegion(line, line, Optional.of(entry), panel.clicks()));
        }
        return new RenderBody(lines, regions);
    }

    private RenderBody text(
            Player player,
            BoardDefinition board,
            ViewDefinition view,
            PanelDefinition panel,
            ViewerSession session,
            Optional<PanelEntry> selected
    ) {
        List<RenderedLine> lines = content.content(player, board, view, panel, session, selected)
                .map(provided -> provided.stream().map(line -> plain(line, panel)).toList())
                .orElseGet(() -> panel.lines().stream()
                        .map(line -> new RenderedLine(
                                templates.render(line.template(), player, board.id(), view.id(), selected, session.state()),
                                line.scaleOr(panel.style().scale())))
                        .toList());
        List<ClickRegion> regions = panel.clicks().isEmpty() ? List.of()
                : List.of(ClickRegion.wholePanel(selected, panel.clicks()));
        return new RenderBody(lines, regions);
    }

    private RenderBody buttons(
            Player player,
            BoardDefinition board,
            ViewDefinition view,
            PanelDefinition panel,
            ViewerSession session,
            Optional<PanelEntry> selected
    ) {
        List<RenderedLine> lines = new ArrayList<>();
        List<ClickRegion> regions = new ArrayList<>();
        for (ButtonDefinition button : panel.buttons()) {
            if (!conditions.test(button.condition(), player, board, view, panel.id(), session, selected)) {
                continue;
            }
            int line = lines.size();
            lines.add(plain(templates.render(button.text(), player, board.id(), view.id(), selected, session.state()), panel));
            regions.add(new ClickRegion(line, line, selected, button.clicks()));
        }
        return new RenderBody(lines, regions);
    }

    private int spaces(double width) {
        return Math.max(1, (int) Math.round(width * PIXELS_PER_BLOCK / SPACE_PIXELS));
    }

    private RenderedLine plain(Component text, PanelDefinition panel) {
        return new RenderedLine(text, panel.style().scale());
    }

    private Optional<PanelEntry> selected(PanelDefinition panel, ViewerSession session) {
        return panel.selectionPanel().flatMap(session::selectedEntry);
    }

    private Component join(List<RenderedLine> lines) {
        Component result = Component.empty();
        for (int index = 0; index < lines.size(); index++) {
            if (index > 0) {
                result = result.append(Component.newline());
            }
            result = result.append(lines.get(index).text());
        }
        return result;
    }
}
