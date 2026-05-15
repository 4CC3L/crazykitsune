package com.crazykitsune.backend.service;

import com.crazykitsune.backend.domain.Card;
import com.crazykitsune.backend.domain.GameSettings;
import com.crazykitsune.backend.domain.Player;
import com.crazykitsune.backend.domain.Room;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DeckService {

    private final RulesService rulesService;

    public DeckService(RulesService rulesService) {
        this.rulesService = rulesService;
    }

    public void prepareDeck(Room room) {
        List<Card> deck = new ArrayList<>();
        int handOrder = 0;
        for (int deckNumber = 1; deckNumber <= GameSettings.DECKS; deckNumber++) {
            for (String suit : rulesService.standardSuits()) {
                for (String rank : rulesService.standardRanks()) {
                    deck.add(new Card(cardId(deckNumber, suit, rank), rank, suit, handOrder++));
                }
            }
            deck.add(new Card(cardId(deckNumber, "JOKER", "JOKER-A"), "JOKER", "JOKER", handOrder++));
            deck.add(new Card(cardId(deckNumber, "JOKER", "JOKER-B"), "JOKER", "JOKER", handOrder++));
        }
        Collections.shuffle(deck);
        room.getDrawPile().clear();
        deck.forEach(room.getDrawPile()::push);
        room.getDiscardPile().clear();
        room.setMatchRank(null);
        room.setMatchSuit(null);
        room.setPendingDraw(0);
        room.setExtraTurns(0);
        room.setWinnerId(null);
        room.getPlayers().values().forEach(player -> player.getHand().clear());
    }

    public void dealCards(Room room) {
        for (int count = 0; count < GameSettings.STARTING_CARDS; count++) {
            for (Player player : room.getPlayers().values()) {
                drawCards(room, player, 1);
            }
        }
        room.getPlayers().values().forEach(this::sortHand);
        Card firstOpenCard = room.getDrawPile().pop();
        while (firstOpenCard.isJoker() && !room.getDrawPile().isEmpty()) {
            room.getDrawPile().addLast(firstOpenCard);
            firstOpenCard = room.getDrawPile().pop();
        }
        room.getDiscardPile().add(firstOpenCard);
        room.setMatchRank(firstOpenCard.rank());
        room.setMatchSuit(firstOpenCard.suit());
    }

    public void drawCards(Room room, Player player, int amount) {
        for (int index = 0; index < amount; index++) {
            if (room.getDrawPile().isEmpty()) {
                reshuffle(room);
            }
            if (room.getDrawPile().isEmpty()) {
                return;
            }
            player.getHand().add(room.getDrawPile().pop());
        }
        sortHand(player);
    }

    public void sortHand(Player player) {
        player.getHand().sort(rulesService.handComparator());
    }

    private void reshuffle(Room room) {
        if (room.getDiscardPile().size() <= 1) {
            return;
        }
        Card topCard = room.getDiscardPile().remove(room.getDiscardPile().size() - 1);
        List<Card> recycled = new ArrayList<>(room.getDiscardPile());
        room.getDiscardPile().clear();
        room.getDiscardPile().add(topCard);
        Collections.shuffle(recycled);
        recycled.forEach(room.getDrawPile()::push);
    }

    private String cardId(int deckNumber, String suit, String rank) {
        return deckNumber + "-" + suit + "-" + rank + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}