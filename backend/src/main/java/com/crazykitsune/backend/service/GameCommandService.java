package com.crazykitsune.backend.service;

import com.crazykitsune.backend.domain.Card;
import com.crazykitsune.backend.domain.Player;
import com.crazykitsune.backend.domain.Room;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class GameCommandService {

    private final GameRoomSupport roomSupport;
    private final DeckService deckService;
    private final GameNotificationService notificationService;

    public GameCommandService(
        GameRoomSupport roomSupport,
        DeckService deckService,
        GameNotificationService notificationService
    ) {
        this.roomSupport = roomSupport;
        this.deckService = deckService;
        this.notificationService = notificationService;
    }

    public void startGame(String roomCode, String playerId) {
        Room room = roomSupport.getRoom(roomCode);
        room.getLock().lock();
        try {
            Player player = roomSupport.requirePlayer(room, playerId);
            if (!Objects.equals(room.getHostId(), player.getId())) {
                throw GameErrors.forbidden("Solo el anfitrion puede iniciar la partida.");
            }
            if (room.getPlayers().size() < 2) {
                throw GameErrors.badRequest("Se necesitan al menos 2 jugadores para iniciar.");
            }
            if (room.isStarted()) {
                throw GameErrors.badRequest("La partida ya inicio.");
            }
            deckService.prepareDeck(room);
            deckService.dealCards(room);
            room.setStarted(true);
            room.setCurrentPlayerId(room.getPlayers().values().iterator().next().getId());
            room.incrementVersion();
            room.appendLog("La partida inicio. Turno de " + room.getPlayers().get(room.getCurrentPlayerId()).getName() + ".");
            notificationService.publish(room, "La partida comenzo");
        } finally {
            room.getLock().unlock();
        }
    }

    public void play(String roomCode, String playerId, List<String> cardIds, String declaredSuit, String targetPlayerId) {
        Room room = roomSupport.getRoom(roomCode);
        room.getLock().lock();
        try {
            Player player = roomSupport.assertActiveTurn(room, playerId);
            if (cardIds == null || cardIds.isEmpty()) {
                throw GameErrors.badRequest("Debes seleccionar al menos una carta.");
            }
            List<Card> selectedCards = collectCards(player, cardIds);
            selectedCards.sort(Comparator.comparingInt(Card::handOrder));
            validatePlay(room, selectedCards, declaredSuit);

            for (Card selectedCard : selectedCards) {
                player.getHand().remove(selectedCard);
                room.getDiscardPile().add(selectedCard);
            }

            Card topCard = selectedCards.get(selectedCards.size() - 1);
            if (!topCard.isJoker()) {
                room.setMatchRank(topCard.rank());
                room.setMatchSuit("8".equals(topCard.rank()) ? roomSupport.normalizeSuit(declaredSuit, topCard.suit()) : topCard.suit());
            } else {
                room.setMatchSuit(roomSupport.normalizeSuit(declaredSuit, room.getMatchSuit()));
            }

            StringBuilder summary = new StringBuilder(player.getName())
                .append(" jugo ")
                .append(selectedCards.stream().map(Card::label).collect(Collectors.joining(", ")));

            if ("A".equals(topCard.rank()) && targetPlayerId != null && !targetPlayerId.isBlank()) {
                swapRandomCards(room, player, targetPlayerId);
                summary.append(" e intercambio una carta.");
            }

            if ("Q".equals(topCard.rank())) {
                room.setPendingDraw(room.getPendingDraw() + selectedCards.size() * 2);
                summary.append(" e inicio/continuo un ataque de ").append(room.getPendingDraw()).append(" cartas.");
            }

            if ("K".equals(topCard.rank())) {
                room.setPendingDraw(room.getPendingDraw() + selectedCards.size() * 3);
                summary.append(" e inicio/continuo un ataque de ").append(room.getPendingDraw()).append(" cartas.");
            }

            if ("6".equals(topCard.rank()) && selectedCards.size() == 3) {
                int grenadeDraw = allSameSuit(selectedCards) ? 2 : 1;
                room.getPlayers().values().stream()
                    .filter(other -> !other.getId().equals(player.getId()))
                    .forEach(other -> deckService.drawCards(room, other, grenadeDraw));
                summary.append(" y activo una granada de ").append(grenadeDraw).append(" carta(s) por rival.");
            }

            if ("J".equals(topCard.rank())) {
                room.setExtraTurns(room.getExtraTurns() + selectedCards.size());
                summary.append(" y gano ").append(selectedCards.size()).append(" turno(s) extra.");
            }

            room.incrementVersion();
            room.appendLog(summary.toString());

            if (player.getHand().isEmpty()) {
                room.setWinnerId(player.getId());
                room.appendLog(player.getName() + " gano la partida.");
                notificationService.publish(room, player.getName() + " gano la partida");
                return;
            }

            finishTurn(room, player, topCard, selectedCards.size());
            notificationService.publish(room, summary.toString());
        } finally {
            room.getLock().unlock();
        }
    }

    public void draw(String roomCode, String playerId, boolean fromDiscard) {
        Room room = roomSupport.getRoom(roomCode);
        room.getLock().lock();
        try {
            Player player = roomSupport.assertActiveTurn(room, playerId);
            if (room.getPendingDraw() > 0) {
                int pendingDraw = room.getPendingDraw();
                deckService.drawCards(room, player, pendingDraw);
                room.appendLog(player.getName() + " robo " + pendingDraw + " cartas por ataque.");
                room.setPendingDraw(0);
                room.incrementVersion();
                advanceTurn(room, 1);
                notificationService.publish(room, player.getName() + " robo cartas por ataque");
                return;
            }

            if (fromDiscard) {
                if (room.getDiscardPile().size() <= 1) {
                    throw GameErrors.badRequest("No hay suficientes cartas abiertas para robar del descarte.");
                }
                Card drawn = room.getDiscardPile().remove(room.getDiscardPile().size() - 1);
                player.getHand().add(drawn);
                deckService.sortHand(player);
                room.appendLog(player.getName() + " robo la carta abierta " + drawn.label() + ".");
            } else {
                deckService.drawCards(room, player, 1);
                room.appendLog(player.getName() + " robo una carta cerrada.");
            }

            room.incrementVersion();
            finishTurn(room, player, null, 0);
            notificationService.publish(room, player.getName() + " robo una carta");
        } finally {
            room.getLock().unlock();
        }
    }

    public void pass(String roomCode, String playerId) {
        Room room = roomSupport.getRoom(roomCode);
        room.getLock().lock();
        try {
            Player player = roomSupport.assertActiveTurn(room, playerId);
            if (room.getPendingDraw() > 0) {
                throw GameErrors.badRequest("Debes robar las cartas del ataque antes de pasar.");
            }
            boolean hasPlayable = player.getHand().stream().anyMatch(card -> canPlay(room, card));
            if (hasPlayable) {
                throw GameErrors.badRequest("Aun tienes jugadas posibles.");
            }
            room.incrementVersion();
            room.appendLog(player.getName() + " pasa el turno.");
            finishTurn(room, player, null, 0);
            notificationService.publish(room, player.getName() + " paso");
        } finally {
            room.getLock().unlock();
        }
    }

    private void validatePlay(Room room, List<Card> selectedCards, String declaredSuit) {
        if (!room.isStarted()) {
            throw GameErrors.badRequest("La partida aun no ha iniciado.");
        }
        boolean sameRank = selectedCards.stream().map(Card::rank).distinct().count() == 1;
        if (!sameRank) {
            throw GameErrors.badRequest("El prototipo solo admite jugadas simples, duos y trios del mismo valor.");
        }
        if (selectedCards.size() > 3) {
            throw GameErrors.badRequest("El prototipo admite hasta 3 cartas por jugada.");
        }
        Card anchor = selectedCards.stream().filter(card -> !card.isJoker()).findFirst().orElse(selectedCards.get(0));
        if (!canPlay(room, anchor)) {
            throw GameErrors.badRequest("La jugada no coincide con la carta abierta.");
        }
        if (room.getPendingDraw() > 0) {
            String rank = selectedCards.get(0).rank();
            boolean validDefense = "JOKER".equals(rank) || "Q".equals(rank) || "K".equals(rank);
            if (!validDefense) {
                throw GameErrors.badRequest("Debes defender el ataque con Q, K o JOKER, o robar.");
            }
        }
        Card topCard = selectedCards.get(selectedCards.size() - 1);
        if (("8".equals(topCard.rank()) || topCard.isJoker()) && (declaredSuit == null || declaredSuit.isBlank())) {
            throw GameErrors.badRequest("Debes indicar el nuevo palo al jugar un 8 o JOKER.");
        }
    }

    private void finishTurn(Room room, Player currentPlayer, Card topCard, int cardsPlayed) {
        if (room.getWinnerId() != null) {
            return;
        }

        if (room.getExtraTurns() > 0) {
            room.setExtraTurns(room.getExtraTurns() - 1);
            room.setCurrentPlayerId(currentPlayer.getId());
            room.appendLog("Turno extra para " + currentPlayer.getName() + ".");
            return;
        }

        int steps = 1;
        if (topCard != null && "2".equals(topCard.rank())) {
            steps += Math.max(cardsPlayed, 1);
        }
        advanceTurn(room, steps);
    }

    private void advanceTurn(Room room, int steps) {
        List<Player> order = new ArrayList<>(room.getPlayers().values());
        int currentIndex = indexOf(order, room.getCurrentPlayerId());
        int nextIndex = Math.floorMod(currentIndex + steps, order.size());
        room.setCurrentPlayerId(order.get(nextIndex).getId());
        room.appendLog("Turno de " + order.get(nextIndex).getName() + ".");
    }

    private int indexOf(List<Player> order, String playerId) {
        for (int index = 0; index < order.size(); index++) {
            if (order.get(index).getId().equals(playerId)) {
                return index;
            }
        }
        throw new IllegalStateException("Jugador activo no encontrado en el orden de turnos.");
    }

    private void swapRandomCards(Room room, Player player, String targetPlayerId) {
        Player target = roomSupport.requirePlayer(room, targetPlayerId);
        if (target.getId().equals(player.getId())) {
            throw GameErrors.badRequest("No puedes intercambiar contigo mismo.");
        }
        if (player.getHand().isEmpty() || target.getHand().isEmpty()) {
            throw GameErrors.badRequest("Ambos jugadores deben tener cartas para intercambiar.");
        }
        Card stolen = target.getHand().remove((int) (Math.random() * target.getHand().size()));
        Card returned = player.getHand().remove((int) (Math.random() * player.getHand().size()));
        player.getHand().add(stolen);
        target.getHand().add(returned);
        deckService.sortHand(player);
        deckService.sortHand(target);
        room.appendLog(player.getName() + " intercambio una carta con " + target.getName() + ".");
    }

    private boolean canPlay(Room room, Card card) {
        if (card.isJoker()) {
            return true;
        }
        return card.rank().equals(room.getMatchRank()) || card.suit().equals(room.getMatchSuit());
    }

    private List<Card> collectCards(Player player, List<String> cardIds) {
        List<Card> selectedCards = new ArrayList<>();
        for (String cardId : cardIds) {
            Card selected = player.getHand().stream()
                .filter(card -> card.id().equals(cardId))
                .findFirst()
                .orElseThrow(() -> GameErrors.badRequest("Se detecto una carta invalida en la jugada."));
            if (selectedCards.stream().anyMatch(card -> card.id().equals(selected.id()))) {
                throw GameErrors.badRequest("No puedes repetir la misma carta en la jugada.");
            }
            selectedCards.add(selected);
        }
        return selectedCards;
    }

    private boolean allSameSuit(List<Card> cards) {
        return cards.stream().map(Card::suit).distinct().count() == 1;
    }
}