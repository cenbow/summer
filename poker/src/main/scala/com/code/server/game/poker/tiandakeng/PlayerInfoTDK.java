package com.code.server.game.poker.tiandakeng;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by sunxianping on 2018-10-18.
 */
public class PlayerInfoTDK {
    private List<Integer> cards = new ArrayList<>();

    private List<Integer> bets = new ArrayList<>();

    public List<Integer> getCards() {
        return cards;
    }

    public PlayerInfoTDK setCards(List<Integer> cards) {
        this.cards = cards;
        return this;
    }
}
