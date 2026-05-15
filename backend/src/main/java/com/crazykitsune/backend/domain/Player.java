package com.crazykitsune.backend.domain;

import java.util.ArrayList;
import java.util.List;

public class Player {

    private final String id;
    private final String name;
    private final List<Card> hand = new ArrayList<>();

    public Player(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<Card> getHand() {
        return hand;
    }
}