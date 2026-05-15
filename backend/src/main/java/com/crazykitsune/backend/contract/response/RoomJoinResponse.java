package com.crazykitsune.backend.contract.response;

public record RoomJoinResponse(String roomCode, String playerId, GameView state) {
}