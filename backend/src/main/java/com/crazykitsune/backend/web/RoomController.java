package com.crazykitsune.backend.web;

import com.crazykitsune.backend.contract.request.PlayerRequest;
import com.crazykitsune.backend.contract.response.GameView;
import com.crazykitsune.backend.contract.response.RoomJoinResponse;
import com.crazykitsune.backend.contract.response.RulesView;
import com.crazykitsune.backend.service.RoomService;
import com.crazykitsune.backend.service.RulesService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = {"http://localhost:4200", "http://127.0.0.1:4200"})
public class RoomController {

    private final RoomService roomService;
    private final RulesService rulesService;

    public RoomController(RoomService roomService, RulesService rulesService) {
        this.roomService = roomService;
        this.rulesService = rulesService;
    }

    @PostMapping("/rooms")
    public RoomJoinResponse createRoom(@Valid @RequestBody PlayerRequest request) {
        return roomService.createRoom(request.playerName());
    }

    @PostMapping("/rooms/{roomCode}/join")
    public RoomJoinResponse joinRoom(
        @PathVariable String roomCode,
        @Valid @RequestBody PlayerRequest request
    ) {
        return roomService.joinRoom(roomCode, request.playerName());
    }

    @GetMapping("/rooms/{roomCode}/state")
    public GameView getState(@PathVariable String roomCode, @RequestParam String playerId) {
        return roomService.getState(roomCode, playerId);
    }

    @GetMapping("/rules")
    public RulesView getRules() {
        return rulesService.getRules();
    }
}