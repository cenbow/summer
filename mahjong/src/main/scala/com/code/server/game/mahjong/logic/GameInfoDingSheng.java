package com.code.server.game.mahjong.logic;

import java.util.List;

/**
 * Created by sunxianping on 2018-10-08.
 */
public class GameInfoDingSheng extends GameInfoNew {

    /**
     * 初始化方法
     *
     * @param firstTurn
     * @param users
     */
    public void init(int gameId, long firstTurn, List<Long> users, RoomInfo room) {
        this.gameId = gameId;

        this.firstTurn = firstTurn;
        this.turnId = firstTurn;
        remainCards.addAll(CardTypeUtil.ALL_CARD);
        //不要东西南北
        remainCards.removeAll(CardTypeUtil.FENG_CARD);
        this.users.addAll(users);
        this.room = room;
        this.cardSize = 13;
        this.playerSize = room.getPersonNumber();
        //不带风
        fapai();
    }
}
