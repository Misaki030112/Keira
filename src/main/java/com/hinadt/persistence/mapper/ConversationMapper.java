package com.hinadt.persistence.mapper;

import com.hinadt.persistence.record.ConversationRecord;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

public interface ConversationMapper {

    @Insert("INSERT INTO conversations (player_name, session_id, message_type, message_content, context_data) VALUES (#{playerName}, #{sessionId}, #{messageType}, #{content}, #{contextData})")
    void insertMessage(@Param("playerName") String playerName,
                       @Param("sessionId") String sessionId,
                       @Param("messageType") String messageType,
                       @Param("content") String content,
                       @Param("contextData") String contextData);

    @Select("SELECT session_id, message_type, message_content, `timestamp`, context_data FROM conversations WHERE player_name = #{playerName} AND session_id = #{sessionId} ORDER BY `timestamp` DESC LIMIT #{limit}")
    @ConstructorArgs({
            @Arg(column = "session_id", javaType = String.class, name = "sessionId"),
            @Arg(column = "message_type", javaType = String.class, name = "messageType"),
            @Arg(column = "message_content", javaType = String.class, name = "messageContent"),
            @Arg(column = "timestamp", javaType = java.time.LocalDateTime.class, name = "timestamp"),
            @Arg(column = "context_data", javaType = String.class, name = "contextData")
    })
    List<ConversationRecord> getRecent(@Param("playerName") String playerName,
                                    @Param("sessionId") String sessionId,
                                    @Param("limit") int limit);

    @Delete("DELETE FROM conversations WHERE `timestamp` < DATEADD('DAY', -#{days}, CURRENT_TIMESTAMP)")
    int deleteOlderThan(@Param("days") int days);

    @Select("SELECT COUNT(*) AS total_messages, COUNT(DISTINCT player_name) AS unique_players, COUNT(DISTINCT session_id) AS total_sessions FROM conversations")
    Map<String, Object> getStats();
}
