package com.crazykitsune.backend.contract.response;

public record PlayerSummary(String id, String name, int cardCount, boolean host, boolean currentTurn, boolean winner) {
}