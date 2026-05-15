package com.crazykitsune.backend.contract.response;

import java.util.List;

public record GameView(
    String roomCode,
    String playerId,
    String playerName,
    boolean started,
    boolean host,
    boolean currentTurn,
    String winnerId,
    String winnerName,
    String matchRank,
    String matchSuit,
    int pendingDraw,
    int drawPileCount,
    List<CardView> hand,
    List<CardView> discard,
    List<PlayerSummary> players,
    List<String> log,
    int version
) {
}