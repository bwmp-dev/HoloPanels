package dev.aether.holopanels.api;

import net.kyori.adventure.text.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public final class PanelEntry {
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_.-]+");

    private final String id;
    private final Component label;
    private final Map<String, Component> fields;
    private final Map<String, String> attributes;

    private PanelEntry(Builder builder) {
        this.id = validateId(builder.id);
        this.label = Objects.requireNonNull(builder.label, "label");
        this.fields = Map.copyOf(builder.fields);
        this.attributes = Map.copyOf(builder.attributes);
    }

    public static Builder builder(String id, Component label) {
        return new Builder(id, label);
    }

    public String id() {
        return id;
    }

    public Component label() {
        return label;
    }

    public Map<String, Component> fields() {
        return fields;
    }

    public Map<String, String> attributes() {
        return attributes;
    }

    public Component field(String name) {
        return name.equals("label") ? label : fields.getOrDefault(name, Component.empty());
    }

    public String attribute(String name) {
        return attributes.getOrDefault(name, "");
    }

    private static String validateId(String id) {
        Objects.requireNonNull(id, "id");
        if (!ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("Entry id must match " + ID_PATTERN.pattern() + ": " + id);
        }
        return id;
    }

    public static final class Builder {
        private final String id;
        private final Component label;
        private final Map<String, Component> fields = new LinkedHashMap<>();
        private final Map<String, String> attributes = new LinkedHashMap<>();

        private Builder(String id, Component label) {
            this.id = id;
            this.label = label;
        }

        public Builder field(String name, Component value) {
            fields.put(validateName(name), Objects.requireNonNull(value, "value"));
            return this;
        }

        public Builder attribute(String name, String value) {
            attributes.put(validateName(name), Objects.requireNonNull(value, "value"));
            return this;
        }

        public PanelEntry build() {
            return new PanelEntry(this);
        }

        private static String validateName(String name) {
            Objects.requireNonNull(name, "name");
            if (!ID_PATTERN.matcher(name).matches()) {
                throw new IllegalArgumentException("Field name must match " + ID_PATTERN.pattern() + ": " + name);
            }
            return name;
        }
    }
}
