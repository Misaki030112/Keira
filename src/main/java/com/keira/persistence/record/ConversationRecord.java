package com.keira.persistence.record;

import java.time.LocalDateTime;

/**
 * Immutable conversation row mapped via MyBatis constructor args.
 */
public record ConversationRecord(
        String sessionId,
        String messageType,
        String messageContent,
        LocalDateTime timestamp,
        String contextData
) {}

