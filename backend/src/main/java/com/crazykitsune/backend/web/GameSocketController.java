package com.crazykitsune.backend.web;

import com.crazykitsune.backend.service.GameService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Controller
public class GameSocketController {

    private final GameService gameService;

    public GameSocketController(GameService gameService) {
        this.gameService = gameService;
    }

    @MessageMapping("/rooms/{roomCode}/start")
    public void start(@DestinationVariable String roomCode, @Valid StartRequest request) {
        gameService.startGame(roomCode, request.playerId());
    }

    @MessageMapping("/rooms/{roomCode}/play")
    public void play(@DestinationVariable String roomCode, @Valid PlayRequest request) {
        gameService.play(roomCode, request.playerId(), request.cardIds(), request.declaredSuit(), request.targetPlayerId());
    }

    @MessageMapping("/rooms/{roomCode}/draw")
    public void draw(@DestinationVariable String roomCode, @Valid DrawRequest request) {
        gameService.draw(roomCode, request.playerId(), request.fromDiscard());
    }

    @MessageMapping("/rooms/{roomCode}/pass")
    public void pass(@DestinationVariable String roomCode, @Valid PassRequest request) {
        gameService.pass(roomCode, request.playerId());
    }

    public record StartRequest(@NotBlank String playerId) {
    }

    public record DrawRequest(@NotBlank String playerId, boolean fromDiscard) {
    }

    public record PassRequest(@NotBlank String playerId) {
    }

    public record PlayRequest(
        @NotBlank String playerId,
        @NotEmpty List<String> cardIds,
        String declaredSuit,
        String targetPlayerId
    ) {
    }
}