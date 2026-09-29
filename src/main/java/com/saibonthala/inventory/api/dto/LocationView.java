package com.saibonthala.inventory.api.dto;

import com.saibonthala.inventory.domain.Location;
import com.saibonthala.inventory.domain.LocationType;

public record LocationView(String code, String name, LocationType type) {

    public static LocationView from(Location location) {
        return new LocationView(location.getCode(), location.getName(), location.getType());
    }
}
