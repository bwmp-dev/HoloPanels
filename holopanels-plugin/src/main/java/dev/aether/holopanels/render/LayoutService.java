package dev.aether.holopanels.render;

import dev.aether.holopanels.api.PanelEntry;
import dev.aether.holopanels.model.BoardDefinition;
import dev.aether.holopanels.model.ButtonDefinition;
import dev.aether.holopanels.model.PanelDefinition;
import dev.aether.holopanels.model.ViewDefinition;
import dev.aether.holopanels.runtime.ConditionService;
import dev.aether.holopanels.runtime.ContentService;
import dev.aether.holopanels.runtime.ViewerSession;
import dev.aether.holopanels.text.TemplateService;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class LayoutService {
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
            output.add(new RenderedPanel(
                    board.id(), view.id(), panel.id(), location,
                    join(body.lines()), Math.max(1, body.lines().size()), panel.style(), body.regions()));
        }
        return output;
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

        List<Component> lines = new ArrayList<>();
        for (String header : panel.header()) {
            lines.add(templates.render(header, player, board.id(), view.id(), Optional.empty(), session.state()));
        }
        List<ClickRegion> regions = new ArrayList<>();
        if (entries.isEmpty()) {
            lines.add(templates.render(panel.emptyText(), player, board.id(), view.id(), Optional.empty(), session.state()));
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
            boolean selected = session.selection(panel.id()).map(entry.id()::equals).orElse(false);
            int line = lines.size();
            lines.add(templates.render(selected ? panel.selectedRowTemplate() : panel.rowTemplate(),
                    player, board.id(), view.id(), Optional.of(entry), session.state()));
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
        List<Component> lines = content.content(player, board, view, panel, session, selected)
                .orElseGet(() -> panel.lines().stream()
                        .map(line -> templates.render(line, player, board.id(), view.id(), selected, session.state()))
                        .toList());
        List<ClickRegion> regions = panel.clicks().isEmpty() ? List.of()
                : List.of(new ClickRegion(0, Math.max(0, lines.size() - 1), selected, panel.clicks()));
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
        List<Component> lines = new ArrayList<>();
        List<ClickRegion> regions = new ArrayList<>();
        for (ButtonDefinition button : panel.buttons()) {
            if (!conditions.test(button.condition(), player, board, view, panel.id(), session, selected)) {
                continue;
            }
            int line = lines.size();
            lines.add(templates.render(button.text(), player, board.id(), view.id(), selected, session.state()));
            regions.add(new ClickRegion(line, line, selected, button.clicks()));
        }
        return new RenderBody(lines.isEmpty() ? List.of(Component.empty()) : lines, regions);
    }

    private Optional<PanelEntry> selected(PanelDefinition panel, ViewerSession session) {
        return panel.selectionPanel().flatMap(session::selectedEntry);
    }

    private Component join(List<Component> lines) {
        Component result = Component.empty();
        for (int index = 0; index < lines.size(); index++) {
            if (index > 0) {
                result = result.append(Component.newline());
            }
            result = result.append(lines.get(index));
        }
        return result;
    }

    private record RenderBody(List<Component> lines, List<ClickRegion> regions) {
    }
}
