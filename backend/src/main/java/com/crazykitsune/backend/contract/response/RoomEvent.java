package com.crazykitsune.backend.contract.response;

import java.time.Instant;

public record RoomEvent(String roomCode, int version, String message, Instant at) {
}