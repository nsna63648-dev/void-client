package com.voidclient.module;

public enum Category {
    VISUAL("Visual", "V"), HUD("HUD", "H"), PERFORMANCE("FPS Boost", "F"),
    CRYSTAL("Crystal", "C"), ANCHOR("Anchor", "A"), COMBAT("Combat", "X");

    public final String label, icon;
    Category(String label, String icon) { this.label = label; this.icon = icon; }
}
