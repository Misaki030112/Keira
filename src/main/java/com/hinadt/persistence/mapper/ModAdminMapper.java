package com.hinadt.persistence.mapper;

import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface ModAdminMapper {

    @Select("SELECT permission_level FROM mod_admins WHERE player_name = #{playerName} AND is_active = TRUE")
    Integer getPermissionLevel(@Param("playerName") String playerName);

    @Insert("INSERT INTO mod_admins (player_name, permission_level, granted_by, notes) VALUES (#{playerName}, #{level}, #{grantedBy}, #{notes})")
    int addAdmin(@Param("playerName") String playerName,
                 @Param("level") int level,
                 @Param("grantedBy") String grantedBy,
                 @Param("notes") String notes);

    @Update("UPDATE mod_admins SET is_active = FALSE WHERE player_name = #{playerName}")
    int removeAdmin(@Param("playerName") String playerName);

    @Update("UPDATE mod_admins SET permission_level = #{level}, notes = #{notes} WHERE player_name = #{playerName}")
    int updateLevel(@Param("playerName") String playerName,
                    @Param("level") int level,
                    @Param("notes") String notes);

    @Select("SELECT player_name, permission_level, granted_by, granted_at, notes FROM mod_admins WHERE is_active = TRUE ORDER BY permission_level DESC, granted_at ASC")
    List<Map<String, Object>> listActive();
}

