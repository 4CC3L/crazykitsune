package com.crazykitsune.backend.service;

import com.crazykitsune.backend.contract.response.RoomEvent;
import com.crazykitsune.backend.domain.Room;
import java.time.Instant;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class GameNotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    public GameNotificationService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publish(Room room, String message) {
        messagingTemplate.convertAndSend(
            "/topic/rooms/" + room.getCode(),
            new RoomEvent(room.getCode(), room.getVersion(), message, Instant.now())
        );
    }
}