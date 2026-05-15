package com.crazykitsune.backend.service;

import com.crazykitsune.backend.contract.response.CardView;
import com.crazykitsune.backend.contract.response.GameView;
import com.crazykitsune.backend.contract.response.PlayerSummary;
import com.crazykitsune.backend.domain.Room;
import org.springframework.stereotype.Component;

@Component
public class GameViewMapper {

    private final GameRoomSupport roomSupport;

    public GameViewMapper(GameRoomSupport roomSupport) {
        this.roomSupport = roomSupport;
    }

    public GameView toView(Room room, String playerId) {
        var self = roomSupport.requirePlayer(room, playerId);
        var players = room.getPlayers().values().stream()
            .map(player -> new PlayerSummary(
                player.getId(),
                player.getName(),
                player.getHand().size(),
                player.getId().equals(room.getHostId()),
                player.getId().equals(room.getCurrentPlayerId()),
                player.getId().equals(room.getWinnerId())
            ))
            .toList();

        var hand = self.getHand().stream()
            .map(card -> new CardView(card.id(), card.rank(), card.suit(), card.label()))
            .toList();
        var discard = room.getDiscardPile().stream()
            .skip(Math.max(0, room.getDiscardPile().size() - 12L))
            .map(card -> new CardView(card.id(), card.rank(), card.suit(), card.label()))
            .toList();
        var log = room.getLogs().stream()
            .skip(Math.max(0, room.getLogs().size() - 12L))
            .toList();

        return new GameView(
            room.getCode(),
            playerId,
            self.getName(),
            room.isStarted(),
            room.getHostId().equals(playerId),
            room.getCurrentPlayerId() != null && room.getCurrentPlayerId().equals(playerId),
            room.getWinnerId(),
            room.getWinnerId() != null ? room.getPlayers().get(room.getWinnerId()).getName() : null,
            room.getMatchRank(),
            room.getMatchSuit(),
            room.getPendingDraw(),
            room.getDrawPile().size(),
            hand,
            discard,
            players,
            log,
            room.getVersion()
        );
    }
}