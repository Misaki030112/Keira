package com.hinadt.persistence.mapper;

import com.hinadt.persistence.model.ConversationRow;
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
    @Results(id = "ConversationRowMapping", value = {
            @Result(property = "sessionId", column = "session_id"),
            @Result(property = "messageType", column = "message_type"),
            @Result(property = "messageContent", column = "message_content"),
            @Result(property = "timestamp", column = "timestamp"),
            @Result(property = "contextData", column = "context_data")
    })
    List<ConversationRow> getRecent(@Param("playerName") String playerName,
                                    @Param("sessionId") String sessionId,
                                    @Param("limit") int limit);

    @Delete("DELETE FROM conversations WHERE `timestamp` < DATEADD('DAY', -#{days}, CURRENT_TIMESTAMP)")
    int deleteOlderThan(@Param("days") int days);

    @Select("SELECT COUNT(*) AS total_messages, COUNT(DISTINCT player_name) AS unique_players, COUNT(DISTINCT session_id) AS total_sessions FROM conversations")
    Map<String, Object> getStats();
}
