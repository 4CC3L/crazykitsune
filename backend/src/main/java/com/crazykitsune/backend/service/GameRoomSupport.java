package com.crazykitsune.backend.service;

import com.crazykitsune.backend.domain.Player;
import com.crazykitsune.backend.domain.Room;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class GameRoomSupport {

    private final GameRoomRepository roomRepository;
    private final RulesService rulesService;

    public GameRoomSupport(GameRoomRepository roomRepository, RulesService rulesService) {
        this.roomRepository = roomRepository;
        this.rulesService = rulesService;
    }

    public Room getRoom(String roomCode) {
        return roomRepository.findRequired(roomCode);
    }

    public Player requirePlayer(Room room, String playerId) {
        Player player = room.getPlayers().get(playerId);
        if (player == null) {
            throw GameErrors.notFound("Jugador no encontrado en la sala.");
        }
        return player;
    }

    public Player assertActiveTurn(Room room, String playerId) {
        Player player = requirePlayer(room, playerId);
        if (!room.isStarted()) {
            throw GameErrors.badRequest("La partida aun no ha iniciado.");
        }
        if (room.getWinnerId() != null) {
            throw GameErrors.badRequest("La partida ya termino.");
        }
        if (!Objects.equals(room.getCurrentPlayerId(), playerId)) {
            throw GameErrors.forbidden("No es tu turno.");
        }
        return player;
    }

    public String normalizePlayerName(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            throw GameErrors.badRequest("El nombre del jugador es obligatorio.");
        }
        return playerName.trim();
    }

    public String normalizeSuit(String declaredSuit, String fallback) {
        if (declaredSuit == null || declaredSuit.isBlank()) {
            return fallback;
        }
        String normalized = declaredSuit.trim().toUpperCase(Locale.ROOT);
        if (!rulesService.isValidSuit(normalized)) {
            throw GameErrors.badRequest("El palo indicado no es valido.");
        }
        return normalized;
    }
}