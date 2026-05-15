package com.crazykitsune.backend.domain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantLock;

public class Room {

    private final String code;
    private final LinkedHashMap<String, Player> players = new LinkedHashMap<>();
    private final Deque<Card> drawPile = new ArrayDeque<>();
    private final List<Card> discardPile = new ArrayList<>();
    private final List<String> logs = new ArrayList<>();
    private final ReentrantLock lock = new ReentrantLock();

    private String hostId;
    private String currentPlayerId;
    private String winnerId;
    private String matchRank;
    private String matchSuit;
    private int pendingDraw;
    private int extraTurns;
    private int version;
    private boolean started;

    public Room(String code) {
        this.code = code.toUpperCase(Locale.ROOT);
    }

    public String getCode() {
        return code;
    }

    public LinkedHashMap<String, Player> getPlayers() {
        return players;
    }

    public Deque<Card> getDrawPile() {
        return drawPile;
    }

    public List<Card> getDiscardPile() {
        return discardPile;
    }

    public List<String> getLogs() {
        return logs;
    }

    public ReentrantLock getLock() {
        return lock;
    }

    public String getHostId() {
        return hostId;
    }

    public void setHostId(String hostId) {
        this.hostId = hostId;
    }

    public String getCurrentPlayerId() {
        return currentPlayerId;
    }

    public void setCurrentPlayerId(String currentPlayerId) {
        this.currentPlayerId = currentPlayerId;
    }

    public String getWinnerId() {
        return winnerId;
    }

    public void setWinnerId(String winnerId) {
        this.winnerId = winnerId;
    }

    public String getMatchRank() {
        return matchRank;
    }

    public void setMatchRank(String matchRank) {
        this.matchRank = matchRank;
    }

    public String getMatchSuit() {
        return matchSuit;
    }

    public void setMatchSuit(String matchSuit) {
        this.matchSuit = matchSuit;
    }

    public int getPendingDraw() {
        return pendingDraw;
    }

    public void setPendingDraw(int pendingDraw) {
        this.pendingDraw = pendingDraw;
    }

    public int getExtraTurns() {
        return extraTurns;
    }

    public void setExtraTurns(int extraTurns) {
        this.extraTurns = extraTurns;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public void incrementVersion() {
        this.version++;
    }

    public boolean isStarted() {
        return started;
    }

    public void setStarted(boolean started) {
        this.started = started;
    }

    public void appendLog(String message) {
        logs.add(message);
    }
}