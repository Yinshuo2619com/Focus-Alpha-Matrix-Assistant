package com.educate.assistant.websocket;

import com.educate.assistant.common.JwtUtil;
import com.educate.assistant.service.NotificationService;
import com.educate.assistant.service.RecommendService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    private final JwtUtil jwtUtil;
    private final RecommendService recommendService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final ConcurrentHashMap<Long, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String query = session.getUri() != null ? session.getUri().getQuery() : null;
        String token = null;
        if (query != null) {
            Map<String, String> params = UriComponentsBuilder.newInstance()
                    .query(query).build().getQueryParams().toSingleValueMap();
            token = params.get("token");
        }

        if (token == null || !jwtUtil.validateToken(token)) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        String username = jwtUtil.getUsernameFromToken(token);
        Long userId = recommendService.getUserIdByUsername(username);
        sessions.put(userId, session);

        int unreadCount = notificationService.getUnreadCount(userId);
        sendToUser(userId, Map.of("type", "unread_count", "count", unreadCount));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.values().remove(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket transport error: {}", exception.getMessage());
        sessions.values().remove(session);
    }

    public static void sendUnreadCount(Long userId, int count) {
        try {
            WebSocketSession session = sessions.get(userId);
            if (session != null && session.isOpen()) {
                String message = new ObjectMapper().writeValueAsString(Map.of("type", "unread_count", "count", count));
                session.sendMessage(new TextMessage(message));
            }
        } catch (IOException e) {
            log.error("Failed to send WebSocket message to user {}: {}", userId, e.getMessage());
        }
    }

    private void sendToUser(Long userId, Map<String, Object> data) {
        try {
            WebSocketSession session = sessions.get(userId);
            if (session != null && session.isOpen()) {
                String message = objectMapper.writeValueAsString(data);
                session.sendMessage(new TextMessage(message));
            }
        } catch (IOException e) {
            log.error("Failed to send WebSocket message to user {}: {}", userId, e.getMessage());
        }
    }
}
