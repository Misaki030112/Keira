package com.keira.persistence.mapper;

import com.keira.persistence.record.LocationRecord;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface LocationMapper {

    @Insert("MERGE INTO player_locations (player_name, location_name, world, x, y, z, description, saved_at) KEY(player_name, location_name) VALUES (#{playerName}, #{locationName}, #{world}, #{x}, #{y}, #{z}, #{description}, CURRENT_TIMESTAMP)")
    void upsert(@Param("playerName") String playerName,
                @Param("locationName") String locationName,
                @Param("world") String world,
                @Param("x") double x,
                @Param("y") double y,
                @Param("z") double z,
                @Param("description") String description);

    @Select("SELECT location_name, world, x, y, z, description, saved_at FROM player_locations WHERE player_name = #{playerName} AND location_name = #{locationName}")
    @ConstructorArgs({
            @Arg(column = "location_name", javaType = String.class, name = "locationName"),
            @Arg(column = "world", javaType = String.class, name = "world"),
            @Arg(column = "x", javaType = double.class, name = "x"),
            @Arg(column = "y", javaType = double.class, name = "y"),
            @Arg(column = "z", javaType = double.class, name = "z"),
            @Arg(column = "description", javaType = String.class, name = "description"),
            @Arg(column = "saved_at", javaType = java.time.LocalDateTime.class, name = "savedAt")
    })
    LocationRecord getExact(@Param("playerName") String playerName,
                         @Param("locationName") String locationName);

    @Select("SELECT location_name, world, x, y, z, description, saved_at FROM player_locations WHERE player_name = #{playerName} AND location_name LIKE #{pattern} ORDER BY saved_at DESC LIMIT 1")
    @ConstructorArgs({
            @Arg(column = "location_name", javaType = String.class, name = "locationName"),
            @Arg(column = "world", javaType = String.class, name = "world"),
            @Arg(column = "x", javaType = double.class, name = "x"),
            @Arg(column = "y", javaType = double.class, name = "y"),
            @Arg(column = "z", javaType = double.class, name = "z"),
            @Arg(column = "description", javaType = String.class, name = "description"),
            @Arg(column = "saved_at", javaType = java.time.LocalDateTime.class, name = "savedAt")
    })
    LocationRecord getFuzzy(@Param("playerName") String playerName,
                         @Param("pattern") String pattern);

    @Select("SELECT location_name, world, x, y, z, description, saved_at FROM player_locations WHERE player_name = #{playerName} ORDER BY saved_at DESC")
    @ConstructorArgs({
            @Arg(column = "location_name", javaType = String.class, name = "locationName"),
            @Arg(column = "world", javaType = String.class, name = "world"),
            @Arg(column = "x", javaType = double.class, name = "x"),
            @Arg(column = "y", javaType = double.class, name = "y"),
            @Arg(column = "z", javaType = double.class, name = "z"),
            @Arg(column = "description", javaType = String.class, name = "description"),
            @Arg(column = "saved_at", javaType = java.time.LocalDateTime.class, name = "savedAt")
    })
    List<LocationRecord> getAll(@Param("playerName") String playerName);

    @Delete("DELETE FROM player_locations WHERE player_name = #{playerName} AND location_name = #{locationName}")
    int delete(@Param("playerName") String playerName,
               @Param("locationName") String locationName);
}
