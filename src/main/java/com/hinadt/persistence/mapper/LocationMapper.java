package com.hinadt.persistence.mapper;

import com.hinadt.persistence.model.LocationRow;
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
    @Results(id = "LocationRowMapping", value = {
            @Result(property = "locationName", column = "location_name"),
            @Result(property = "world", column = "world"),
            @Result(property = "x", column = "x"),
            @Result(property = "y", column = "y"),
            @Result(property = "z", column = "z"),
            @Result(property = "description", column = "description"),
            @Result(property = "savedAt", column = "saved_at")
    })
    LocationRow getExact(@Param("playerName") String playerName,
                         @Param("locationName") String locationName);

    @Select("SELECT location_name, world, x, y, z, description, saved_at FROM player_locations WHERE player_name = #{playerName} AND location_name LIKE #{pattern} ORDER BY saved_at DESC LIMIT 1")
    @ResultMap("LocationRowMapping")
    LocationRow getFuzzy(@Param("playerName") String playerName,
                         @Param("pattern") String pattern);

    @Select("SELECT location_name, world, x, y, z, description, saved_at FROM player_locations WHERE player_name = #{playerName} ORDER BY saved_at DESC")
    @ResultMap("LocationRowMapping")
    List<LocationRow> getAll(@Param("playerName") String playerName);

    @Delete("DELETE FROM player_locations WHERE player_name = #{playerName} AND location_name = #{locationName}")
    int delete(@Param("playerName") String playerName,
               @Param("locationName") String locationName);
}
