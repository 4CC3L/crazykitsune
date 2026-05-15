package com.crazykitsune.backend.web;

import com.crazykitsune.backend.service.GameService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

    private final GameService gameService;

    public RoomController(GameService gameService) {
        this.gameService = gameService;
    }

    @PostMapping("/rooms")
    public GameService.RoomJoinResponse createRoom(@Valid @RequestBody PlayerRequest request) {
        return gameService.createRoom(request.playerName());
    }

    @PostMapping("/rooms/{roomCode}/join")
    public GameService.RoomJoinResponse joinRoom(
        @PathVariable String roomCode,
        @Valid @RequestBody PlayerRequest request
    ) {
        return gameService.joinRoom(roomCode, request.playerName());
    }

    @GetMapping("/rooms/{roomCode}/state")
    public GameService.GameView getState(@PathVariable String roomCode, @RequestParam String playerId) {
        return gameService.getState(roomCode, playerId);
    }

    @GetMapping("/rules")
    public GameService.RulesView getRules() {
        return gameService.getRules();
    }

    public record PlayerRequest(@NotBlank String playerName) {
    }
}