package com.hinadt.persistence.model;

import java.time.LocalDateTime;

/**
 * Row model for player_locations table.
 */
public class LocationRow {
    private String locationName;
    private String world;
    private double x;
    private double y;
    private double z;
    private String description;
    private LocalDateTime savedAt;

    public String getLocationName() { return locationName; }
    public void setLocationName(String locationName) { this.locationName = locationName; }

    public String getWorld() { return world; }
    public void setWorld(String world) { this.world = world; }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    public double getZ() { return z; }
    public void setZ(double z) { this.z = z; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getSavedAt() { return savedAt; }
    public void setSavedAt(LocalDateTime savedAt) { this.savedAt = savedAt; }
}

