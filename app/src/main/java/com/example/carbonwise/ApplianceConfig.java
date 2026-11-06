package com.example.carbonwise;

public class ApplianceConfig {
    private String name;
    private int wattage;
    private boolean isAlwaysOn;
    private boolean isOwned;

    public ApplianceConfig() {}

    public ApplianceConfig(String name, int wattage, boolean isAlwaysOn, boolean isOwned) {
        this.name = name;
        this.wattage = wattage;
        this.isAlwaysOn = isAlwaysOn;
        this.isOwned = isOwned;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getWattage() { return wattage; }
    public void setWattage(int wattage) { this.wattage = wattage; }

    public boolean isAlwaysOn() { return isAlwaysOn; }
    public void setAlwaysOn(boolean alwaysOn) { isAlwaysOn = alwaysOn; }

    public boolean isOwned() { return isOwned; }
    public void setOwned(boolean owned) { isOwned = owned; }
}