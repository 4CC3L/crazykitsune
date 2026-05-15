package com.crazykitsune.backend.domain;

public record Card(String id, String rank, String suit, int handOrder) {

    public boolean isJoker() {
        return "JOKER".equals(rank);
    }

    public String label() {
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