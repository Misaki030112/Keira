package com.keira.persistence.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

public interface PlayerConnectionMapper {

    @Insert("INSERT INTO player_connections (uuid, player_name, ip, locale) VALUES (#{uuid}, #{playerName}, #{ip}, #{locale})")
    int insertConnection(@Param("uuid") String uuid,
                         @Param("playerName") String playerName,
                         @Param("ip") String ip,
                         @Param("locale") String locale);
}

