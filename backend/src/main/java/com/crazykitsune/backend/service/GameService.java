package com.crazykitsune.backend.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GameService {

    private static final int MAX_PLAYERS = 10;
    private static final int STARTING_CARDS = 8;
    private static final int DECKS = 3;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final List<String> STANDARD_SUITS = List.of("HEARTS", "DIAMONDS", "CLUBS", "SPADES");
    private static final List<String> STANDARD_RANKS = List.of("A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K");
    private static final Map<String, Integer> CARD_ORDER = Map.ofEntries(
        Map.entry("A", 1),
        Map.entry("2", 2),
        Map.entry("3", 3),
        Map.entry("4", 4),
        Map.entry("5", 5),
        Map.entry("6", 6),
        Map.entry("7", 7),
        Map.entry("8", 8),
        Map.entry("9", 9),
        Map.entry("10", 10),
        Map.entry("J", 11),
        Map.entry("Q", 12),
        Map.entry("K", 13),
        Map.entry("JOKER", 99)
    );

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final SimpMessagingTemplate messagingTemplate;

    public GameService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public RoomJoinResponse createRoom(String rawPlayerName) {
        String playerName = normalizePlayerName(rawPlayerName);
        Room room = new Room(generateCode());
        Player player = new Player(UUID.randomUUID().toString(), playerName);
        room.players.put(player.id, player);
        room.hostId = player.id;
        room.appendLog(player.name + " creo la sala.");
        rooms.put(room.code, room);
        publish(room, "Sala creada por " + player.name);
        return new RoomJoinResponse(room.code, player.id, toView(room, player.id));
    }

    public RoomJoinResponse joinRoom(String roomCode, String rawPlayerName) {
        Room room = getRoom(roomCode);
        room.lock.lock();
        try {
            if (room.started) {
                throw badRequest("La partida ya inicio.");
            }
            if (room.players.size() >= MAX_PLAYERS) {
                throw badRequest("La sala llego al maximo de 10 jugadores.");
            }
            String playerName = normalizePlayerName(rawPlayerName);
            boolean repeated = room.players.values().stream().anyMatch(player -> player.name.equalsIgnoreCase(playerName));
            if (repeated) {
                throw badRequest("Ya existe un jugador con ese nombre en la sala.");
            }
            Player player = new Player(UUID.randomUUID().toString(), playerName);
            room.players.put(player.id, player);
            room.version++;
            room.appendLog(player.name + " se unio a la sala.");
            publish(room, player.name + " se unio a la sala");
            return new RoomJoinResponse(room.code, player.id, toView(room, player.id));
        } finally {
            room.lock.unlock();
        }
    }

    public GameView getState(String roomCode, String playerId) {
        Room room = getRoom(roomCode);
        room.lock.lock();
        try {
            requirePlayer(room, playerId);
            return toView(room, playerId);
        } finally {
            room.lock.unlock();
        }
    }

    public RulesView getRules() {
        return new RulesView(
            "CRAZY KITSUNE",
            List.of(
                "Partidas multijugador para 2 a 10 personas.",
                "Se usan 3 barajas inglesas completas con jokers.",
                "Cada jugador recibe 8 cartas y solo ve su propia mano.",
                "La carta abierta define el valor o palo con el que se puede jugar.",
                "Si no puedes jugar, robas carta. El prototipo permite robar del mazo o del descarte.",
                "Gana quien se quede sin cartas. El prototipo marca victoria directa y deja visibles las reglas avanzadas del documento."
            ),
            List.of(
                "A: intercambio sencillo contra un objetivo cuando se indica un jugador destino.",
                "2: salta al siguiente jugador; en combo salta a varios.",
                "8: cambia el palo activo para la siguiente jugada.",
                "J: concede turnos adicionales acumulables.",
                "Q y K: inician o prolongan ataques de robo.",
                "JOKER: comodin para jugar o defender ataques."
            ),
            List.of(
                "El motor inicial automatiza jugadas simples, duos y trios del mismo valor.",
                "Tambien automatiza ataques Q/K, salto con 2, cambio de palo con 8, turno extra con J, granada de 6 y victorias por mano vacia.",
                "Las reglas avanzadas del documento siguen visibles en la interfaz como referencia: escalera, operacion matematica, anulacion de victoria y muerte subita."
            )
        );
    }

    public void startGame(String roomCode, String playerId) {
        Room room = getRoom(roomCode);
        room.lock.lock();
        try {
            Player player = requirePlayer(room, playerId);
            if (!Objects.equals(room.hostId, player.id)) {
                throw forbidden("Solo el anfitrion puede iniciar la partida.");
            }
            if (room.players.size() < 2) {
                throw badRequest("Se necesitan al menos 2 jugadores para iniciar.");
            }
            if (room.started) {
                throw badRequest("La partida ya inicio.");
            }
            prepareDeck(room);
            dealCards(room);
            room.started = true;
            room.currentPlayerId = room.players.values().iterator().next().id;
            room.version++;
            room.appendLog("La partida inicio. Turno de " + room.players.get(room.currentPlayerId).name + ".");
            publish(room, "La partida comenzo");
        } finally {
            room.lock.unlock();
        }
    }

    public void play(String roomCode, String playerId, List<String> cardIds, String declaredSuit, String targetPlayerId) {
        Room room = getRoom(roomCode);
        room.lock.lock();
        try {
            Player player = assertActiveTurn(room, playerId);
            if (cardIds == null || cardIds.isEmpty()) {
                throw badRequest("Debes seleccionar al menos una carta.");
            }
            List<Card> selectedCards = collectCards(player, cardIds);
            selectedCards.sort(Comparator.comparingInt(card -> card.handOrder));
            validatePlay(room, selectedCards, declaredSuit);

            for (Card selectedCard : selectedCards) {
                player.hand.remove(selectedCard);
                room.discardPile.add(selectedCard);
            }

            Card topCard = selectedCards.get(selectedCards.size() - 1);
            Card anchorCard = selectedCards.stream().filter(card -> !card.isJoker()).findFirst().orElse(topCard);
            if (!topCard.isJoker()) {
                room.matchRank = topCard.rank;
                room.matchSuit = "8".equals(topCard.rank) ? normalizeSuit(declaredSuit, topCard.suit) : topCard.suit;
            } else {
                room.matchSuit = normalizeSuit(declaredSuit, room.matchSuit);
            }

            StringBuilder summary = new StringBuilder(player.name)
                .append(" jugo ")
                .append(selectedCards.stream().map(Card::label).collect(Collectors.joining(", ")));

            if ("A".equals(topCard.rank) && targetPlayerId != null && !targetPlayerId.isBlank()) {
                swapRandomCards(room, player, targetPlayerId);
                summary.append(" e intercambio una carta.");
            }

            if ("Q".equals(topCard.rank)) {
                room.pendingDraw += selectedCards.size() * 2;
                summary.append(" e inicio/continuo un ataque de ").append(room.pendingDraw).append(" cartas.");
            }

            if ("K".equals(topCard.rank)) {
                room.pendingDraw += selectedCards.size() * 3;
                summary.append(" e inicio/continuo un ataque de ").append(room.pendingDraw).append(" cartas.");
            }

            if ("6".equals(topCard.rank) && selectedCards.size() == 3) {
                int grenadeDraw = allSameSuit(selectedCards) ? 2 : 1;
                room.players.values().stream()
                    .filter(other -> !other.id.equals(player.id))
                    .forEach(other -> drawCards(room, other, grenadeDraw));
                summary.append(" y activo una granada de ").append(grenadeDraw).append(" carta(s) por rival.");
            }

            if ("J".equals(topCard.rank)) {
                room.extraTurns += selectedCards.size();
                summary.append(" y gano ").append(selectedCards.size()).append(" turno(s) extra.");
            }

            room.version++;
            room.appendLog(summary.toString());

            if (player.hand.isEmpty()) {
                room.winnerId = player.id;
                room.appendLog(player.name + " gano la partida.");
                publish(room, player.name + " gano la partida");
                return;
            }

            finishTurn(room, player, topCard, selectedCards.size());
            publish(room, summary.toString());
        } finally {
            room.lock.unlock();
        }
    }

    public void draw(String roomCode, String playerId, boolean fromDiscard) {
        Room room = getRoom(roomCode);
        room.lock.lock();
        try {
            Player player = assertActiveTurn(room, playerId);
            if (room.pendingDraw > 0) {
                drawCards(room, player, room.pendingDraw);
                room.appendLog(player.name + " robo " + room.pendingDraw + " cartas por ataque.");
                room.pendingDraw = 0;
                room.version++;
                advanceTurn(room, 1);
                publish(room, player.name + " robo cartas por ataque");
                return;
            }

            if (fromDiscard) {
                if (room.discardPile.size() <= 1) {
                    throw badRequest("No hay suficientes cartas abiertas para robar del descarte.");
                }
                Card drawn = room.discardPile.remove(room.discardPile.size() - 1);
                player.hand.add(drawn);
                sortHand(player);
                room.appendLog(player.name + " robo la carta abierta " + drawn.label() + ".");
            } else {
                drawCards(room, player, 1);
                room.appendLog(player.name + " robo una carta cerrada.");
            }

            room.version++;
            finishTurn(room, player, null, 0);
            publish(room, player.name + " robo una carta");
        } finally {
            room.lock.unlock();
        }
    }

    public void pass(String roomCode, String playerId) {
        Room room = getRoom(roomCode);
        room.lock.lock();
        try {
            Player player = assertActiveTurn(room, playerId);
            if (room.pendingDraw > 0) {
                throw badRequest("Debes robar las cartas del ataque antes de pasar.");
            }
            boolean hasPlayable = player.hand.stream().anyMatch(card -> canPlay(room, card));
            if (hasPlayable) {
                throw badRequest("Aun tienes jugadas posibles.");
            }
            room.version++;
            room.appendLog(player.name + " pasa el turno.");
            finishTurn(room, player, null, 0);
            publish(room, player.name + " paso");
        } finally {
            room.lock.unlock();
        }
    }

    private void validatePlay(Room room, List<Card> selectedCards, String declaredSuit) {
        if (!room.started) {
            throw badRequest("La partida aun no ha iniciado.");
        }
        boolean sameRank = selectedCards.stream().map(card -> card.rank).distinct().count() == 1;
        if (!sameRank) {
            throw badRequest("El prototipo solo admite jugadas simples, duos y trios del mismo valor.");
        }
        if (selectedCards.size() > 3) {
            throw badRequest("El prototipo admite hasta 3 cartas por jugada.");
        }
        Card anchor = selectedCards.stream().filter(card -> !card.isJoker()).findFirst().orElse(selectedCards.get(0));
        if (!canPlay(room, anchor)) {
            throw badRequest("La jugada no coincide con la carta abierta.");
        }
        if (room.pendingDraw > 0) {
            String rank = selectedCards.get(0).rank;
            boolean validDefense = "JOKER".equals(rank) || "Q".equals(rank) || "K".equals(rank);
            if (!validDefense) {
                throw badRequest("Debes defender el ataque con Q, K o JOKER, o robar.");
            }
        }
        Card topCard = selectedCards.get(selectedCards.size() - 1);
        if ("8".equals(topCard.rank) && (declaredSuit == null || declaredSuit.isBlank())) {
            throw badRequest("Debes indicar el nuevo palo al jugar un 8.");
        }
    }

    private void finishTurn(Room room, Player currentPlayer, Card topCard, int cardsPlayed) {
        if (room.winnerId != null) {
            return;
        }

        if (room.extraTurns > 0) {
            room.extraTurns--;
            room.currentPlayerId = currentPlayer.id;
            room.appendLog("Turno extra para " + currentPlayer.name + ".");
            return;
        }

        int steps = 1;
        if (topCard != null && "2".equals(topCard.rank)) {
            steps += Math.max(cardsPlayed, 1);
        }
        advanceTurn(room, steps);
    }

    private void advanceTurn(Room room, int steps) {
        List<Player> order = new ArrayList<>(room.players.values());
        int currentIndex = indexOf(order, room.currentPlayerId);
        int nextIndex = Math.floorMod(currentIndex + steps, order.size());
        room.currentPlayerId = order.get(nextIndex).id;
        room.appendLog("Turno de " + order.get(nextIndex).name + ".");
    }

    private int indexOf(List<Player> order, String playerId) {
        for (int index = 0; index < order.size(); index++) {
            if (order.get(index).id.equals(playerId)) {
                return index;
            }
        }
        throw new IllegalStateException("Jugador activo no encontrado en el orden de turnos.");
    }

    private void swapRandomCards(Room room, Player player, String targetPlayerId) {
        Player target = requirePlayer(room, targetPlayerId);
        if (target.id.equals(player.id)) {
            throw badRequest("No puedes intercambiar contigo mismo.");
        }
        if (player.hand.isEmpty() || target.hand.isEmpty()) {
            throw badRequest("Ambos jugadores deben tener cartas para intercambiar.");
        }
        Card stolen = target.hand.remove(RANDOM.nextInt(target.hand.size()));
        Card returned = player.hand.remove(RANDOM.nextInt(player.hand.size()));
        player.hand.add(stolen);
        target.hand.add(returned);
        sortHand(player);
        sortHand(target);
        room.appendLog(player.name + " intercambio una carta con " + target.name + ".");
    }

    private boolean canPlay(Room room, Card card) {
        if (card.isJoker()) {
            return true;
        }
        return card.rank.equals(room.matchRank) || card.suit.equals(room.matchSuit);
    }

    private List<Card> collectCards(Player player, List<String> cardIds) {
        List<Card> selectedCards = new ArrayList<>();
        for (String cardId : cardIds) {
            Card selected = player.hand.stream()
                .filter(card -> card.id.equals(cardId))
                .findFirst()
                .orElseThrow(() -> badRequest("Se detecto una carta invalida en la jugada."));
            if (selectedCards.stream().anyMatch(card -> card.id.equals(selected.id))) {
                throw badRequest("No puedes repetir la misma carta en la jugada.");
            }
            selectedCards.add(selected);
        }
        return selectedCards;
    }

    private Player assertActiveTurn(Room room, String playerId) {
        Player player = requirePlayer(room, playerId);
        if (!room.started) {
            throw badRequest("La partida aun no ha iniciado.");
        }
        if (room.winnerId != null) {
            throw badRequest("La partida ya termino.");
        }
        if (!Objects.equals(room.currentPlayerId, playerId)) {
            throw forbidden("No es tu turno.");
        }
        return player;
    }

    private Player requirePlayer(Room room, String playerId) {
        Player player = room.players.get(playerId);
        if (player == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Jugador no encontrado en la sala.");
        }
        return player;
    }

    private Room getRoom(String roomCode) {
        Room room = rooms.get(roomCode.toUpperCase(Locale.ROOT));
        if (room == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sala no encontrada.");
        }
        return room;
    }

    private void prepareDeck(Room room) {
        List<Card> deck = new ArrayList<>();
        int handOrder = 0;
        for (int deckNumber = 1; deckNumber <= DECKS; deckNumber++) {
            for (String suit : STANDARD_SUITS) {
                for (String rank : STANDARD_RANKS) {
                    deck.add(new Card(cardId(deckNumber, suit, rank), rank, suit, handOrder++));
                }
            }
            deck.add(new Card(cardId(deckNumber, "JOKER", "JOKER-A"), "JOKER", "JOKER", handOrder++));
            deck.add(new Card(cardId(deckNumber, "JOKER", "JOKER-B"), "JOKER", "JOKER", handOrder++));
        }
        Collections.shuffle(deck);
        room.drawPile.clear();
        deck.forEach(room.drawPile::push);
        room.discardPile.clear();
        room.matchRank = null;
        room.matchSuit = null;
        room.pendingDraw = 0;
        room.extraTurns = 0;
        room.winnerId = null;
        room.players.values().forEach(player -> player.hand.clear());
    }

    private void dealCards(Room room) {
        for (int count = 0; count < STARTING_CARDS; count++) {
            for (Player player : room.players.values()) {
                drawCards(room, player, 1);
            }
        }
        for (Player player : room.players.values()) {
            sortHand(player);
        }
        Card firstOpenCard = room.drawPile.pop();
        while (firstOpenCard.isJoker() && !room.drawPile.isEmpty()) {
            room.drawPile.addLast(firstOpenCard);
            firstOpenCard = room.drawPile.pop();
        }
        room.discardPile.add(firstOpenCard);
        room.matchRank = firstOpenCard.rank;
        room.matchSuit = firstOpenCard.suit;
    }

    private void drawCards(Room room, Player player, int amount) {
        for (int index = 0; index < amount; index++) {
            if (room.drawPile.isEmpty()) {
                reshuffle(room);
            }
            if (room.drawPile.isEmpty()) {
                return;
            }
            player.hand.add(room.drawPile.pop());
        }
        sortHand(player);
    }

    private void reshuffle(Room room) {
        if (room.discardPile.size() <= 1) {
            return;
        }
        Card topCard = room.discardPile.remove(room.discardPile.size() - 1);
        List<Card> recycled = new ArrayList<>(room.discardPile);
        room.discardPile.clear();
        room.discardPile.add(topCard);
        Collections.shuffle(recycled);
        recycled.forEach(room.drawPile::push);
    }

    private void sortHand(Player player) {
        player.hand.sort(Comparator.comparingInt((Card card) -> CARD_ORDER.getOrDefault(card.rank, 999))
            .thenComparing(card -> card.suit));
    }

    private boolean allSameSuit(List<Card> cards) {
        return cards.stream().map(card -> card.suit).distinct().count() == 1;
    }

    private String normalizePlayerName(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            throw badRequest("El nombre del jugador es obligatorio.");
        }
        return playerName.trim();
    }

    private String normalizeSuit(String declaredSuit, String fallback) {
        if (declaredSuit == null || declaredSuit.isBlank()) {
            return fallback;
        }
        String normalized = declaredSuit.trim().toUpperCase(Locale.ROOT);
        if (!STANDARD_SUITS.contains(normalized)) {
            throw badRequest("El palo indicado no es valido.");
        }
        return normalized;
    }

    private String generateCode() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        while (true) {
            StringBuilder builder = new StringBuilder();
            for (int index = 0; index < 5; index++) {
                builder.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
            }
            String code = builder.toString();
            if (!rooms.containsKey(code)) {
                return code;
            }
        }
    }

    private String cardId(int deckNumber, String suit, String rank) {
        return deckNumber + "-" + suit + "-" + rank + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private void publish(Room room, String message) {
        messagingTemplate.convertAndSend("/topic/rooms/" + room.code, new RoomEvent(room.code, room.version, message, Instant.now()));
    }

    private GameView toView(Room room, String playerId) {
        Player self = requirePlayer(room, playerId);
        List<PlayerSummary> players = room.players.values().stream()
            .map(player -> new PlayerSummary(
                player.id,
                player.name,
                player.hand.size(),
                player.id.equals(room.hostId),
                player.id.equals(room.currentPlayerId),
                player.id.equals(room.winnerId)
            ))
            .toList();

        List<CardView> hand = self.hand.stream().map(card -> new CardView(card.id, card.rank, card.suit, card.label())).toList();
        List<CardView> discard = room.discardPile.stream()
            .skip(Math.max(0, room.discardPile.size() - 12L))
            .map(card -> new CardView(card.id, card.rank, card.suit, card.label()))
            .toList();
        List<String> log = room.logs.stream()
            .skip(Math.max(0, room.logs.size() - 12L))
            .toList();

        return new GameView(
            room.code,
            playerId,
            self.name,
            room.started,
            room.hostId.equals(playerId),
            room.currentPlayerId != null && room.currentPlayerId.equals(playerId),
            room.winnerId,
            room.winnerId != null ? room.players.get(room.winnerId).name : null,
            room.matchRank,
            room.matchSuit,
            room.pendingDraw,
            room.drawPile.size(),
            hand,
            discard,
            players,
            log,
            room.version
        );
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }

    public record RoomJoinResponse(String roomCode, String playerId, GameView state) {
    }

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

    public record CardView(String id, String rank, String suit, String label) {
    }

    public record PlayerSummary(String id, String name, int cardCount, boolean host, boolean currentTurn, boolean winner) {
    }

    public record RulesView(String gameName, List<String> basics, List<String> poweredCards, List<String> implementationNotes) {
    }

    public record RoomEvent(String roomCode, int version, String message, Instant at) {
    }

    private static final class Room {
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

        private Room(String code) {
            this.code = code.toUpperCase(Locale.ROOT);
        }

        private void appendLog(String message) {
            logs.add(message);
        }
    }

    private static final class Player {
        private final String id;
        private final String name;
        private final List<Card> hand = new ArrayList<>();

        private Player(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private record Card(String id, String rank, String suit, int handOrder) {
        private boolean isJoker() {
            return "JOKER".equals(rank);
        }

        private String label() {
            return isJoker() ? "JOKER" : rank + " de " + translateSuit(suit);
        }

        private static String translateSuit(String suit) {
            return switch (suit) {
                case "HEARTS" -> "Corazones";
                case "DIAMONDS" -> "Diamantes";
                case "CLUBS" -> "Treboles";
                case "SPADES" -> "Espadas";
                default -> suit;
            };
        }
    }
}