package com.crazykitsune.backend.service;

import com.crazykitsune.backend.domain.Room;
import com.crazykitsune.backend.generated.model.CardView;
import com.crazykitsune.backend.generated.model.GameView;
import com.crazykitsune.backend.generated.model.PlayerSummary;
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
            .map(player -> new PlayerSummary()
                .id(player.getId())
                .name(player.getName())
                .cardCount(player.getHand().size())
                .host(player.getId().equals(room.getHostId()))
                .currentTurn(player.getId().equals(room.getCurrentPlayerId()))
                .winner(player.getId().equals(room.getWinnerId()))
            )
            .toList();

        var hand = self.getHand().stream()
            .map(card -> new CardView()
                .id(card.id())
                .rank(card.rank())
                .suit(card.suit())
                .label(card.label())
            )
            .toList();
        var discard = room.getDiscardPile().stream()
            .skip(Math.max(0, room.getDiscardPile().size() - 12L))
            .map(card -> new CardView()
                .id(card.id())
                .rank(card.rank())
                .suit(card.suit())
                .label(card.label())
            )
            .toList();
        var log = room.getLogs().stream()
            .skip(Math.max(0, room.getLogs().size() - 12L))
            .toList();

        return new GameView()
            .roomCode(room.getCode())
            .playerId(playerId)
            .playerName(self.getName())
            .started(room.isStarted())
            .host(room.getHostId().equals(playerId))
            .currentTurn(room.getCurrentPlayerId() != null && room.getCurrentPlayerId().equals(playerId))
            .winnerId(room.getWinnerId())
            .winnerName(room.getWinnerId() != null ? room.getPlayers().get(room.getWinnerId()).getName() : null)
            .matchRank(room.getMatchRank())
            .matchSuit(room.getMatchSuit())
            .pendingDraw(room.getPendingDraw())
            .drawPileCount(room.getDrawPile().size())
            .hand(hand)
            .discard(discard)
            .players(players)
            .log(log)
            .version(room.getVersion());
    }
}