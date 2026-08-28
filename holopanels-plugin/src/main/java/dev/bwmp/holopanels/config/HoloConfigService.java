package dev.bwmp.holopanels.config;

import dev.bwmp.holopanels.api.ClickType;
import dev.bwmp.holopanels.model.ActionDefinition;
import dev.bwmp.holopanels.model.BoardDefinition;
import dev.bwmp.holopanels.model.BoardLocation;
import dev.bwmp.holopanels.model.ButtonDefinition;
import dev.bwmp.holopanels.model.ConditionDefinition;
import dev.bwmp.holopanels.model.ConfigSnapshot;
import dev.bwmp.holopanels.model.PanelDefinition;
import dev.bwmp.holopanels.model.PanelLine;
import dev.bwmp.holopanels.model.PanelOffset;
import dev.bwmp.holopanels.model.PanelSize;
import dev.bwmp.holopanels.model.PanelStyle;
import dev.bwmp.holopanels.model.PanelType;
import dev.bwmp.holopanels.model.PluginSettings;
import dev.bwmp.holopanels.model.StaticEntryDefinition;
import dev.bwmp.holopanels.model.ViewDefinition;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public final class HoloConfigService {
    private final JavaPlugin plugin;
    private final File boardsFile;
    private final File viewsDirectory;

    public HoloConfigService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.boardsFile = new File(plugin.getDataFolder(), "boards.yml");
        this.viewsDirectory = new File(plugin.getDataFolder(), "views");
    }

    public void createDefaults() {
        plugin.saveDefaultConfig();
        saveIfMissing("boards.yml");
        saveIfMissing("messages.yml");
        saveIfMissing("views/example.yml");
    }

    public ConfigSnapshot load() throws ConfigException {
        plugin.reloadConfig();
        PluginSettings settings = settings(plugin.getConfig());
        Map<NamespacedKey, ViewDefinition> views = loadViews(settings);
        Map<NamespacedKey, BoardDefinition> boards = loadBoards(settings);

        for (BoardDefinition board : boards.values()) {
            if (!views.containsKey(board.rootView())) {
                plugin.getLogger().warning("Board " + board.id() + " is waiting for unregistered view " + board.rootView() + ".");
            }
        }

        return new ConfigSnapshot(settings, boards, views);
    }

    public void moveBoard(NamespacedKey id, Location location) throws ConfigException {
        YamlConfiguration yaml = loadYaml(boardsFile);
        String path = "boards." + configKeyOf(yaml, id) + ".anchor";

        yaml.set(path + ".world", location.getWorld().getName());
        yaml.set(path + ".x", location.getX());
        yaml.set(path + ".y", location.getY());
        yaml.set(path + ".z", location.getZ());
        yaml.set(path + ".yaw", location.getYaw());
        // Always level. A board's normal is computed with the pitch discarded,
        // so a tilted anchor would render where its click regions are not.
        yaml.set(path + ".pitch", 0.0F);
        save(yaml);
    }

    public void unplaceBoard(NamespacedKey id) throws ConfigException {
        YamlConfiguration yaml = loadYaml(boardsFile);
        yaml.set("boards." + configKeyOf(yaml, id) + ".anchor", null);
        save(yaml);
    }

    public void removeBoard(NamespacedKey id) throws ConfigException {
        YamlConfiguration yaml = loadYaml(boardsFile);
        yaml.set("boards." + configKeyOf(yaml, id), null);
        save(yaml);
    }

    /**
     * The key {@code id} is actually written under in boards.yml.
     * <p>
     * An id may be spelled bare ({@code welcome}) or namespaced
     * ({@code holopanels:welcome}) and both load to the same key, so matching
     * on the parsed id rather than on the text is what lets either spelling be
     * edited from in game.
     */
    private String configKeyOf(YamlConfiguration yaml, NamespacedKey id) throws ConfigException {
        ConfigurationSection root = yaml.getConfigurationSection("boards");
        if (root != null) {
            for (String rawId : root.getKeys(false)) {
                if (id.equals(NamespacedKey.fromString(rawId.toLowerCase(Locale.ROOT), plugin))) {
                    return rawId;
                }
            }
        }
        throw new ConfigException("No configured board exists with id " + id);
    }

    private void save(YamlConfiguration yaml) throws ConfigException {
        try {
            yaml.save(boardsFile);
        } catch (IOException exception) {
            throw new ConfigException("Could not save " + boardsFile.getName(), exception);
        }
    }

    private PluginSettings settings(ConfigurationSection root) throws ConfigException {
        PanelStyle style = style(root.getConfigurationSection("style"), null);
        return new PluginSettings(
                positive(root.getInt("visibility-check-ticks", 10), "visibility-check-ticks"),
                positive(root.getInt("hover-check-ticks", 2), "hover-check-ticks"),
                positive(root.getInt("placeholder-refresh-ticks", 20), "placeholder-refresh-ticks"),
                positive(root.getDouble("movement-threshold", 0.5), "movement-threshold"),
                positive(root.getDouble("default-visibility-distance", 24.0), "default-visibility-distance"),
                positive(root.getDouble("default-click-distance", 6.0), "default-click-distance"),
                positive(root.getInt("limits.panels-per-view", 12), "limits.panels-per-view"),
                positive(root.getInt("limits.lines-per-panel", 64), "limits.lines-per-panel"),
                positive(root.getInt("limits.entries-per-provider", 500), "limits.entries-per-provider"),
                positive(root.getInt("limits.actions-per-chain", 16), "limits.actions-per-chain"),
                style
        );
    }

    private Map<NamespacedKey, ViewDefinition> loadViews(PluginSettings settings) throws ConfigException {
        File[] files = viewsDirectory.listFiles((directory, name) -> name.endsWith(".yml"));
        if (files == null) {
            throw new ConfigException("Could not read views directory " + viewsDirectory);
        }

        List<File> ordered = new ArrayList<>(List.of(files));
        ordered.sort(Comparator.comparing(File::getName));
        Map<NamespacedKey, ViewDefinition> views = new LinkedHashMap<>();
        for (File file : ordered) {
            YamlConfiguration yaml = loadYaml(file);
            NamespacedKey id = key(yaml.getString("id", file.getName().replace(".yml", "")), file + ".id");
            ConfigurationSection panelsSection = requiredSection(yaml, "panels", file.toString());
            if (panelsSection.getKeys(false).size() > settings.maxPanelsPerView()) {
                throw new ConfigException(file + " defines more than " + settings.maxPanelsPerView() + " panels");
            }

            Map<String, PanelDefinition> panels = new LinkedHashMap<>();
            for (String panelId : panelsSection.getKeys(false)) {
                ConfigurationSection panelSection = requiredSection(panelsSection, panelId, file.toString());
                panels.put(panelId, panel(panelId, panelSection, settings, file.toString()));
            }

            if (views.put(id, new ViewDefinition(id, panels)) != null) {
                throw new ConfigException("Duplicate view id " + id);
            }
        }
        return views;
    }

    private Map<NamespacedKey, BoardDefinition> loadBoards(PluginSettings settings) throws ConfigException {
        YamlConfiguration yaml = loadYaml(boardsFile);
        ConfigurationSection root = yaml.getConfigurationSection("boards");
        if (root == null) {
            return Map.of();
        }

        Map<NamespacedKey, BoardDefinition> boards = new LinkedHashMap<>();
        for (String rawId : root.getKeys(false)) {
            String path = "boards." + rawId;
            ConfigurationSection section = requiredSection(root, rawId, boardsFile.toString());
            NamespacedKey id = key(rawId, path);
            NamespacedKey view = key(requiredString(section, "root-view", path), path + ".root-view");
            ConfigurationSection anchor = section.getConfigurationSection("anchor");
            Optional<BoardLocation> location = Optional.empty();
            if (anchor != null && !anchor.getString("world", "").isBlank()) {
                location = Optional.of(new BoardLocation(
                        anchor.getString("world", ""),
                        anchor.getDouble("x"),
                        anchor.getDouble("y"),
                        anchor.getDouble("z"),
                        (float) anchor.getDouble("yaw"),
                        (float) anchor.getDouble("pitch")
                ));
            }

            BoardDefinition board = new BoardDefinition(
                    id,
                    view,
                    location,
                    positive(section.getDouble("visibility-distance", settings.defaultVisibilityDistance()), path + ".visibility-distance"),
                    positive(section.getDouble("click-distance", settings.defaultClickDistance()), path + ".click-distance"),
                    condition(section.get("visible-if"), path + ".visible-if")
            );
            if (boards.put(id, board) != null) {
                throw new ConfigException("Duplicate board id " + id);
            }
        }
        return boards;
    }

    private PanelDefinition panel(String id, ConfigurationSection section, PluginSettings settings, String source) throws ConfigException {
        String path = source + ".panels." + id;
        PanelType type;
        try {
            type = PanelType.parse(requiredString(section, "type", path));
        } catch (IllegalArgumentException exception) {
            throw new ConfigException(path + ".type must be list, text, or buttons", exception);
        }

        ConfigurationSection offset = section.getConfigurationSection("offset");
        PanelOffset panelOffset = offset == null
                ? new PanelOffset(0.0, 0.0, 0.0)
                : new PanelOffset(offset.getDouble("right"), offset.getDouble("up"), offset.getDouble("forward"));
        PanelStyle panelStyle = style(section.getConfigurationSection("style"), settings.defaultStyle());
        Optional<NamespacedKey> entryProvider = Optional.empty();
        ConfigurationSection sourceSection = section.getConfigurationSection("source");
        if (sourceSection != null && sourceSection.isString("provider")) {
            entryProvider = Optional.of(key(sourceSection.getString("provider", ""), path + ".source.provider"));
        }

        Optional<NamespacedKey> contentProvider = section.isString("content-provider")
                ? Optional.of(key(section.getString("content-provider", ""), path + ".content-provider"))
                : Optional.empty();
        Optional<String> selection = Optional.ofNullable(section.getString("selection")).filter(value -> !value.isBlank());
        List<StaticEntryDefinition> entries = entries(section.getConfigurationSection("entries"), path);
        List<String> header = section.getStringList("header");
        List<PanelLine> lines = lines(section.getList("lines"), path);
        List<ButtonDefinition> buttons = buttons(section.getMapList("buttons"), path);
        Map<ClickType, List<ActionDefinition>> clicks = clicks(section.getConfigurationSection("clicks"), path);

        int estimatedLines = switch (type) {
            case LIST -> header.size() + Math.max(1, Math.min(entries.size(), section.getInt("page-size", 16)));
            case TEXT -> Math.max(1, lines.size());
            case BUTTONS -> Math.max(1, buttons.size());
        };
        if (estimatedLines > settings.maxLinesPerPanel()) {
            throw new ConfigException(path + " can render more than " + settings.maxLinesPerPanel() + " lines");
        }

        if (type == PanelType.LIST && entryProvider.isPresent() && !entries.isEmpty()) {
            throw new ConfigException(path + " cannot define both source.provider and static entries");
        }
        if (type == PanelType.TEXT && contentProvider.isPresent() && !lines.isEmpty()) {
            throw new ConfigException(path + " cannot define both content-provider and static lines");
        }

        return new PanelDefinition(
                id,
                type,
                panelOffset,
                panelStyle,
                condition(section.get("visible-if"), path + ".visible-if"),
                entryProvider,
                contentProvider,
                selection,
                entries,
                header,
                lines,
                section.getString("row", "<entry:label>"),
                section.getString("selected-row", "<white>> </white><entry:label>"),
                section.getString("heading-row", "<entry:label>"),
                section.getString("empty", "<gray>No entries."),
                positive(section.getInt("page-size", 16), path + ".page-size"),
                buttons,
                clicks
        );
    }

    /**
     * A line is either the text itself, or a map that also gives it a scale.
     * The second form is what puts a headline and its caption in one panel: a
     * text display draws all of its text at one size, so a line that asks for
     * another gets a display of its own.
     */
    private List<PanelLine> lines(List<?> raw, String path) throws ConfigException {
        if (raw == null) {
            return List.of();
        }
        List<PanelLine> lines = new ArrayList<>();
        for (int index = 0; index < raw.size(); index++) {
            Object item = raw.get(index);
            if (!(item instanceof Map<?, ?>) && !(item instanceof ConfigurationSection)) {
                lines.add(PanelLine.of(string(item)));
                continue;
            }
            Map<?, ?> value = map(item);
            String itemPath = path + ".lines[" + index + "]";
            OptionalDouble scale = value.containsKey("scale")
                    ? OptionalDouble.of(positive(number(value.get("scale"), itemPath + ".scale"), itemPath + ".scale"))
                    : OptionalDouble.empty();
            lines.add(new PanelLine(string(value.get("text")), scale));
        }
        return lines;
    }

    private static double number(Object value, String path) throws ConfigException {
        if (value instanceof Number found) {
            return found.doubleValue();
        }
        try {
            return Double.parseDouble(string(value));
        } catch (NumberFormatException exception) {
            throw new ConfigException(path + " must be a number", exception);
        }
    }

    private List<StaticEntryDefinition> entries(ConfigurationSection section, String path) {
        if (section == null) {
            return List.of();
        }

        List<StaticEntryDefinition> entries = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null) {
                continue;
            }
            entries.add(new StaticEntryDefinition(
                    id,
                    entry.getString("label", id),
                    stringMap(entry.getConfigurationSection("fields")),
                    stringMap(entry.getConfigurationSection("attributes"))
            ));
        }
        return entries;
    }

    private List<ButtonDefinition> buttons(List<Map<?, ?>> values, String path) throws ConfigException {
        List<ButtonDefinition> buttons = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            Map<?, ?> value = values.get(index);
            String itemPath = path + ".buttons[" + index + "]";
            String id = string(value.containsKey("id") ? value.get("id") : "button-" + index);
            String text = string(value.containsKey("text") ? value.get("text") : id);
            buttons.add(new ButtonDefinition(
                    id,
                    text,
                    condition(value.get("visible-if"), itemPath + ".visible-if"),
                    clicks(map(value.get("clicks")), itemPath)
            ));
        }
        return buttons;
    }

    private Map<ClickType, List<ActionDefinition>> clicks(ConfigurationSection section, String path) throws ConfigException {
        return clicks(section == null ? Map.of() : section.getValues(false), path);
    }

    private Map<ClickType, List<ActionDefinition>> clicks(Map<?, ?> values, String path) throws ConfigException {
        Map<ClickType, List<ActionDefinition>> result = new EnumMap<>(ClickType.class);
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            ClickType clickType;
            try {
                clickType = ClickType.valueOf(string(entry.getKey()).replace('-', '_').toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new ConfigException(path + ".clicks has unknown click type " + entry.getKey());
            }

            if (!(entry.getValue() instanceof List<?> list)) {
                throw new ConfigException(path + ".clicks." + entry.getKey() + " must be a list");
            }
            List<ActionDefinition> actions = new ArrayList<>();
            for (Object item : list) {
                Map<?, ?> action = map(item);
                String type = string(action.get("type"));
                if (type.isBlank()) {
                    throw new ConfigException(path + " contains an action without a type");
                }
                Map<String, String> arguments = new LinkedHashMap<>();
                for (Map.Entry<?, ?> argument : action.entrySet()) {
                    String name = string(argument.getKey());
                    if (!name.equals("type") && !name.equals("continue-on-failure")) {
                        arguments.put(name, string(argument.getValue()));
                    }
                }
                Object continueValue = action.containsKey("continue-on-failure")
                        ? action.get("continue-on-failure")
                        : false;
                actions.add(new ActionDefinition(type.toLowerCase(Locale.ROOT), arguments,
                        Boolean.parseBoolean(string(continueValue))));
            }
            result.put(clickType, List.copyOf(actions));
        }
        return result;
    }

    private ConditionDefinition condition(Object raw, String path) throws ConfigException {
        if (raw == null) {
            return ConditionDefinition.always();
        }
        Map<?, ?> value = raw instanceof ConfigurationSection section ? section.getValues(false) : map(raw);
        if (value.containsKey("all")) {
            return composite(ConditionDefinition.Kind.ALL, value.get("all"), path);
        }
        if (value.containsKey("any")) {
            return composite(ConditionDefinition.Kind.ANY, value.get("any"), path);
        }
        if (value.containsKey("not")) {
            return new ConditionDefinition(ConditionDefinition.Kind.NOT, Map.of(),
                    List.of(condition(value.get("not"), path + ".not")));
        }
        if (value.containsKey("permission")) {
            return simple(ConditionDefinition.Kind.PERMISSION, "permission", value.get("permission"));
        }
        if (value.containsKey("selected")) {
            return simple(ConditionDefinition.Kind.SELECTED, "panel", value.get("selected"));
        }
        if (value.containsKey("entry-attribute")) {
            return new ConditionDefinition(ConditionDefinition.Kind.ENTRY_ATTRIBUTE,
                    stringMap(map(value.get("entry-attribute"))), List.of());
        }
        if (value.containsKey("session-state")) {
            return new ConditionDefinition(ConditionDefinition.Kind.SESSION_STATE,
                    stringMap(map(value.get("session-state"))), List.of());
        }
        if (value.containsKey("custom")) {
            return new ConditionDefinition(ConditionDefinition.Kind.CUSTOM,
                    stringMap(map(value.get("custom"))), List.of());
        }
        throw new ConfigException(path + " has no recognized condition");
    }

    private ConditionDefinition composite(ConditionDefinition.Kind kind, Object raw, String path) throws ConfigException {
        if (!(raw instanceof List<?> list)) {
            throw new ConfigException(path + " must contain a list");
        }
        List<ConditionDefinition> children = new ArrayList<>();
        for (int index = 0; index < list.size(); index++) {
            children.add(condition(list.get(index), path + "[" + index + "]"));
        }
        return new ConditionDefinition(kind, Map.of(), children);
    }

    private ConditionDefinition simple(ConditionDefinition.Kind kind, String name, Object value) {
        return new ConditionDefinition(kind, Map.of(name, string(value)), List.of());
    }

    private PanelStyle style(ConfigurationSection section, PanelStyle fallback) throws ConfigException {
        PanelStyle base = fallback == null
                ? new PanelStyle(0.25, 1.0, Optional.empty(), 0.0, 4.0, 200, 0xA60B1420, OptionalInt.empty(),
                        255, "left", "fixed", false, false)
                : fallback;
        if (section == null) {
            return base;
        }

        ConfigurationSection sizeSection = section.getConfigurationSection("size");
        Optional<PanelSize> size = sizeSection == null ? base.size() : Optional.of(new PanelSize(
                positive(sizeSection.getDouble("width"), "style.size.width"),
                positive(sizeSection.getDouble("height"), "style.size.height")));
        int argb = argb(section, "background-color", "background-opacity", base.backgroundColor());
        // Either hover key on its own is enough to opt in; the one left out
        // falls back to this panel's own background rather than the inherited
        // hover colour, so a panel that recolours its background does not hover
        // to something unrelated.
        OptionalInt hover = section.isSet("hover-background-color") || section.isSet("hover-background-opacity")
                ? OptionalInt.of(argb(section, "hover-background-color", "hover-background-opacity", argb))
                : base.hoverBackgroundColor();
        return new PanelStyle(
                positive(section.getDouble("line-height", base.lineHeight()), "style.line-height"),
                positive(section.getDouble("scale", base.scale()), "style.scale"),
                size,
                section.getDouble("click-offset-y", base.clickOffsetY()),
                positive(section.getDouble("interaction-width", base.interactionWidth()), "style.interaction-width"),
                positive(section.getInt("line-width", base.lineWidth()), "style.line-width"),
                argb,
                hover,
                Math.max(0, Math.min(255, section.getInt("text-opacity", base.textOpacity()))),
                section.getString("alignment", base.alignment()),
                section.getString("billboard", base.billboard()),
                section.getBoolean("text-shadow", base.textShadow()),
                section.getBoolean("see-through", base.seeThrough())
        );
    }

    private static int argb(ConfigurationSection section, String colorKey, String opacityKey, int base) throws ConfigException {
        double opacity = section.getDouble(opacityKey, ((base >>> 24) & 0xFF) / 255.0);
        if (opacity < 0.0 || opacity > 1.0) {
            throw new ConfigException(opacityKey + " must be between 0 and 1");
        }
        return ((int) Math.round(opacity * 255.0) << 24) | parseRgb(section.getString(colorKey, String.format("#%06x", base & 0xFFFFFF)));
    }

    private NamespacedKey key(String input, String path) throws ConfigException {
        NamespacedKey key = NamespacedKey.fromString(input.toLowerCase(Locale.ROOT), plugin);
        if (key == null) {
            throw new ConfigException(path + " is not a valid namespaced id: " + input);
        }
        return key;
    }

    private YamlConfiguration loadYaml(File file) throws ConfigException {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
            return yaml;
        } catch (IOException | InvalidConfigurationException exception) {
            throw new ConfigException("Could not parse " + file, exception);
        }
    }

    private void saveIfMissing(String resource) {
        File file = new File(plugin.getDataFolder(), resource);
        if (!file.exists()) {
            plugin.saveResource(resource, false);
        }
    }

    private static ConfigurationSection requiredSection(ConfigurationSection parent, String name, String path) throws ConfigException {
        ConfigurationSection section = parent.getConfigurationSection(name);
        if (section == null) {
            throw new ConfigException(path + "." + name + " must be a section");
        }
        return section;
    }

    private static String requiredString(ConfigurationSection section, String name, String path) throws ConfigException {
        String value = section.getString(name, "");
        if (value.isBlank()) {
            throw new ConfigException(path + "." + name + " is required");
        }
        return value;
    }

    private static int positive(int value, String path) throws ConfigException {
        if (value <= 0) {
            throw new ConfigException(path + " must be greater than zero");
        }
        return value;
    }

    private static double positive(double value, String path) throws ConfigException {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new ConfigException(path + " must be a finite value greater than zero");
        }
        return value;
    }

    private static int parseRgb(String input) throws ConfigException {
        String value = input.startsWith("#") ? input.substring(1) : input;
        if (!value.matches("[0-9a-fA-F]{6}")) {
            throw new ConfigException("Invalid RGB color: " + input);
        }
        return Integer.parseInt(value, 16);
    }

    private static Map<String, String> stringMap(ConfigurationSection section) {
        return section == null ? Map.of() : stringMap(section.getValues(false));
    }

    private static Map<String, String> stringMap(Map<?, ?> source) {
        Map<String, String> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(string(key), string(value)));
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<?, ?> map(Object value) throws ConfigException {
        if (value == null) {
            return Map.of();
        }
        if (value instanceof ConfigurationSection section) {
            return section.getValues(false);
        }
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        throw new ConfigException("Expected a configuration section but found " + value.getClass().getSimpleName());
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
