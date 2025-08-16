package com.hinadt.persistence.model;

import java.time.LocalDateTime;

/**
 * Row model for conversations table.
 * Mirrors the schema exactly; MyBatis maps columns by @Results in ConversationMapper.
 */
public class ConversationRow {
    private String sessionId;
    private String messageType;
    private String messageContent;
    private LocalDateTime timestamp;
    private String contextData;

    public ConversationRow() {}

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getMessageContent() {
        return messageContent;
    }

    public void setMessageContent(String messageContent) {
        this.messageContent = messageContent;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getContextData() {
        return contextData;
    }

    public void setContextData(String contextData) {
        this.contextData = contextData;
    }
}

