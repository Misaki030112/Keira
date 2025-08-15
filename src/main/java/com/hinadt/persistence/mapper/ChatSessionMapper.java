package com.hinadt.persistence.mapper;

import org.apache.ibatis.annotations.*;

public interface ChatSessionMapper {

    @Select("SELECT in_chat FROM ai_chat_sessions WHERE player_name = #{playerName}")
    Boolean isInChat(@Param("playerName") String playerName);

    @Insert("MERGE INTO ai_chat_sessions (player_name, in_chat, updated_at) KEY(player_name) VALUES (#{playerName}, TRUE, CURRENT_TIMESTAMP)")
    void enter(@Param("playerName") String playerName);

    @Insert("MERGE INTO ai_chat_sessions (player_name, in_chat, updated_at) KEY(player_name) VALUES (#{playerName}, FALSE, CURRENT_TIMESTAMP)")
    void exit(@Param("playerName") String playerName);

    @Select("SELECT COUNT(*) FROM ai_chat_sessions WHERE in_chat = TRUE")
    int countInChat();

    @Delete("DELETE FROM ai_chat_sessions WHERE updated_at < DATEADD('HOUR', -#{hours}, CURRENT_TIMESTAMP)")
    int deleteOlderThan(@Param("hours") int hours);

    @Delete("DELETE FROM ai_chat_sessions WHERE player_name = #{playerName}")
    int delete(@Param("playerName") String playerName);
}
