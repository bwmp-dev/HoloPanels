package dev.bwmp.holopanels.model;

import java.util.Locale;

public enum PanelType {
    LIST,
    TEXT,
    BUTTONS;

    public static PanelType parse(String input) {
        return valueOf(input.trim().toUpperCase(Locale.ROOT));
    }
}
