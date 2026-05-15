package com.crazykitsune.backend.web;

import com.crazykitsune.backend.generated.api.RoomsApi;
import com.crazykitsune.backend.generated.model.GameView;
import com.crazykitsune.backend.generated.model.PlayerRequest;
import com.crazykitsune.backend.generated.model.RoomJoinResponse;
import com.crazykitsune.backend.service.RoomService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = {"http://localhost:4200", "http://127.0.0.1:4200"})
public class RoomController implements RoomsApi {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @Override
    public ResponseEntity<RoomJoinResponse> createRoom(@Valid PlayerRequest request) {
        return ResponseEntity.ok(roomService.createRoom(request.getPlayerName()));
    }

    @Override
    public ResponseEntity<RoomJoinResponse> joinRoom(String roomCode, @Valid PlayerRequest request) {
        return ResponseEntity.ok(roomService.joinRoom(roomCode, request.getPlayerName()));
    }

    @Override
    public ResponseEntity<GameView> getRoomState(String roomCode, String playerId) {
        return ResponseEntity.ok(roomService.getState(roomCode, playerId));
    }
}