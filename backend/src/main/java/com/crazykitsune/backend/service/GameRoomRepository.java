package com.crazykitsune.backend.service;

import com.crazykitsune.backend.domain.Room;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class GameRoomRepository {

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    public void save(Room room) {
        rooms.put(room.getCode(), room);
    }

    public Room findRequired(String roomCode) {
        Room room = rooms.get(roomCode.toUpperCase());
        if (room == null) {
            throw GameErrors.notFound("Sala no encontrada.");
        }
        return room;
    }

    public boolean exists(String roomCode) {
        return rooms.containsKey(roomCode.toUpperCase());
    }
}