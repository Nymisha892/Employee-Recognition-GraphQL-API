package com.example.recognitionapi.model;

import java.time.Instant;


public record Recognition(
        String id,
        String senderId,
        String recipientId,
        String message,
        Visibility visibility,
        boolean isAnonymous,
        String createdAt
) {

    public static Recognition create(String senderId, String recipientId, String message,  Visibility visibility, boolean isAnonymous) {
        return new Recognition(
                java.util.UUID.randomUUID().toString(),
                senderId,
                recipientId,
                message,
                visibility,
                isAnonymous,
                Instant.now().toString()
        );
    }
}

