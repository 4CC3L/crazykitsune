package com.crazykitsune.backend.service;

import com.crazykitsune.backend.contract.response.GameView;
import com.crazykitsune.backend.contract.response.RoomJoinResponse;
import com.crazykitsune.backend.domain.GameSettings;
import com.crazykitsune.backend.domain.Player;
import com.crazykitsune.backend.domain.Room;
import java.security.SecureRandom;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RoomService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final GameRoomRepository roomRepository;
    private final GameRoomSupport roomSupport;
    private final GameNotificationService notificationService;
    private final GameViewMapper gameViewMapper;

    public RoomService(
        GameRoomRepository roomRepository,
        GameRoomSupport roomSupport,
        GameNotificationService notificationService,
        GameViewMapper gameViewMapper
    ) {
        this.roomRepository = roomRepository;
        this.roomSupport = roomSupport;
        this.notificationService = notificationService;
        this.gameViewMapper = gameViewMapper;
    }

    public RoomJoinResponse createRoom(String rawPlayerName) {
        String playerName = roomSupport.normalizePlayerName(rawPlayerName);
        Room room = new Room(generateCode());
        Player player = new Player(UUID.randomUUID().toString(), playerName);
        room.getPlayers().put(player.getId(), player);
        room.setHostId(player.getId());
        room.appendLog(player.getName() + " creo la sala.");
        roomRepository.save(room);
        notificationService.publish(room, "Sala creada por " + player.getName());
        return new RoomJoinResponse(room.getCode(), player.getId(), gameViewMapper.toView(room, player.getId()));
    }

    public RoomJoinResponse joinRoom(String roomCode, String rawPlayerName) {
        Room room = roomSupport.getRoom(roomCode);
        room.getLock().lock();
        try {
            if (room.isStarted()) {
                throw GameErrors.badRequest("La partida ya inicio.");
            }
            if (room.getPlayers().size() >= GameSettings.MAX_PLAYERS) {
                throw GameErrors.badRequest("La sala llego al maximo de 10 jugadores.");
            }
            String playerName = roomSupport.normalizePlayerName(rawPlayerName);
            boolean repeated = room.getPlayers().values().stream().anyMatch(player -> player.getName().equalsIgnoreCase(playerName));
            if (repeated) {
                throw GameErrors.badRequest("Ya existe un jugador con ese nombre en la sala.");
            }
            Player player = new Player(UUID.randomUUID().toString(), playerName);
            room.getPlayers().put(player.getId(), player);
            room.incrementVersion();
            room.appendLog(player.getName() + " se unio a la sala.");
            notificationService.publish(room, player.getName() + " se unio a la sala");
            return new RoomJoinResponse(room.getCode(), player.getId(), gameViewMapper.toView(room, player.getId()));
        } finally {
            room.getLock().unlock();
        }
    }

    public GameView getState(String roomCode, String playerId) {
        Room room = roomSupport.getRoom(roomCode);
        room.getLock().lock();
        try {
            roomSupport.requirePlayer(room, playerId);
            return gameViewMapper.toView(room, playerId);
        } finally {
            room.getLock().unlock();
        }
    }

    private String generateCode() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        while (true) {
            StringBuilder builder = new StringBuilder();
            for (int index = 0; index < 5; index++) {
                builder.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
            }
            String code = builder.toString();
            if (!roomRepository.exists(code)) {
                return code;
            }
        }
    }
}