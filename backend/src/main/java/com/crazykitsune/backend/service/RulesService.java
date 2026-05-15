package com.crazykitsune.backend.service;

import com.crazykitsune.backend.contract.response.RulesView;
import com.crazykitsune.backend.domain.Card;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class RulesService {

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

    public List<String> standardSuits() {
        return STANDARD_SUITS;
    }

    public List<String> standardRanks() {
        return STANDARD_RANKS;
    }

    public boolean isValidSuit(String suit) {
        return STANDARD_SUITS.contains(suit);
    }

    public Comparator<Card> handComparator() {
        return Comparator.comparingInt((Card card) -> CARD_ORDER.getOrDefault(card.rank(), 999))
            .thenComparing(Card::suit);
    }
}