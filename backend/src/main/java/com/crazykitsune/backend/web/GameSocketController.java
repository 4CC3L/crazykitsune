package com.crazykitsune.backend.web;

import com.crazykitsune.backend.contract.request.DrawCardRequest;
import com.crazykitsune.backend.contract.request.PassTurnRequest;
import com.crazykitsune.backend.contract.request.PlayCardsRequest;
import com.crazykitsune.backend.contract.request.StartGameRequest;
import com.crazykitsune.backend.service.GameCommandService;
import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Controller
public class GameSocketController {

    private final GameCommandService gameCommandService;

    public GameSocketController(GameCommandService gameCommandService) {
        this.gameCommandService = gameCommandService;
    }

    @MessageMapping("/rooms/{roomCode}/start")
    public void start(@DestinationVariable String roomCode, @Valid StartGameRequest request) {
        gameCommandService.startGame(roomCode, request.playerId());
    }

    @MessageMapping("/rooms/{roomCode}/play")
    public void play(@DestinationVariable String roomCode, @Valid PlayCardsRequest request) {
        gameCommandService.play(roomCode, request.playerId(), request.cardIds(), request.declaredSuit(), request.targetPlayerId());
    }

    @MessageMapping("/rooms/{roomCode}/draw")
    public void draw(@DestinationVariable String roomCode, @Valid DrawCardRequest request) {
        gameCommandService.draw(roomCode, request.playerId(), request.fromDiscard());
    }

    @MessageMapping("/rooms/{roomCode}/pass")
    public void pass(@DestinationVariable String roomCode, @Valid PassTurnRequest request) {
        gameCommandService.pass(roomCode, request.playerId());
    }
}