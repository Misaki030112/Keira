package com.hinadt.persistence.mapper;

import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface ConversationMapper {

    @Insert("INSERT INTO conversations (player_name, session_id, message_type, message_content, context_data) VALUES (#{playerName}, #{sessionId}, #{messageType}, #{content}, #{contextData})")
    void insertMessage(@Param("playerName") String playerName,
                       @Param("sessionId") String sessionId,
                       @Param("messageType") String messageType,
                       @Param("content") String content,
                       @Param("contextData") String contextData);

    // Get the session_id of the most recent message for this player.
    // H2 does not allow ORDER BY on a column not in the select list when DISTINCT is used.
    // DISTINCT is unnecessary here because ordering by timestamp and limiting to 1 already
    // picks the latest row's session_id.
    @Select("SELECT session_id FROM conversations WHERE player_name = #{playerName} ORDER BY timestamp DESC LIMIT 1")
    String getLatestSessionId(@Param("playerName") String playerName);

    @Select("SELECT session_id, message_type, message_content AS content, `timestamp` AS created_at, context_data FROM conversations WHERE player_name = #{playerName} AND session_id = #{sessionId} ORDER BY timestamp DESC LIMIT #{limit}")
    List<Map<String, Object>> getRecent(@Param("playerName") String playerName,
                                        @Param("sessionId") String sessionId,
                                        @Param("limit") int limit);

    @Delete("DELETE FROM conversations WHERE timestamp < DATEADD('DAY', -#{days}, CURRENT_TIMESTAMP)")
    int deleteOlderThan(@Param("days") int days);

    @Select("SELECT COUNT(*) AS total_messages, COUNT(DISTINCT player_name) AS unique_players, COUNT(DISTINCT session_id) AS total_sessions FROM conversations")
    Map<String, Object> getStats();
}
