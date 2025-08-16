package com.hinadt.persistence.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface PlayerProfileMapper {

    @Update("UPDATE player_profiles SET last_seen = CURRENT_TIMESTAMP, last_ip = #{ip}, last_locale = #{locale}, total_joins = total_joins + 1, player_name = #{playerName} WHERE uuid = #{uuid}")
    int touchProfile(@Param("uuid") String uuid,
                     @Param("playerName") String playerName,
                     @Param("ip") String ip,
                     @Param("locale") String locale);

    @Insert("INSERT INTO player_profiles (uuid, player_name, first_seen, last_seen, last_ip, last_locale, total_joins) VALUES (#{uuid}, #{playerName}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, #{ip}, #{locale}, 1)")
    int insertProfile(@Param("uuid") String uuid,
                      @Param("playerName") String playerName,
                      @Param("ip") String ip,
                      @Param("locale") String locale);

    @Select("SELECT last_locale FROM player_profiles WHERE uuid = #{uuid} LIMIT 1")
    String selectLastLocaleByUuid(@Param("uuid") String uuid);
}
