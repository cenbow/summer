package com.code.server.constant.response;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by sunxianping on 2017/3/27.
 */
public class GameResultHitGoldFlower {
    public List<IfacePlayerInfoVo> playerCardInfos = new ArrayList<>();
    public int multiple;
    public List<Integer> tableCards = new ArrayList<>();
    public Map<Long, Double> userScores = new HashMap<>();
    public List<Long> winnerList = new ArrayList<>();
    public Long bankerId;
    public List<Map<Long, Long>> killInfo = new ArrayList<>();

    public List<IfacePlayerInfoVo> getPlayerCardInfos() {
        return playerCardInfos;
    }

    public GameResultHitGoldFlower setPlayerCardInfos(List<IfacePlayerInfoVo> playerCardInfos) {
        this.playerCardInfos = playerCardInfos;
        return this;
    }


    public int getMultiple() {
        return multiple;
    }

    public GameResultHitGoldFlower setMultiple(int multiple) {
        this.multiple = multiple;
        return this;
    }


    public List<Integer> getTableCards() {
        return tableCards;
    }

    public GameResultHitGoldFlower setTableCards(List<Integer> tableCards) {
        this.tableCards = tableCards;
        return this;
    }

    public Map<Long, Double> getUserScores() {
        return userScores;
    }

    public GameResultHitGoldFlower setUserScores(Map<Long, Double> userScores) {
        this.userScores = userScores;
        return this;
    }

    public List<Long> getWinnerList() {
        return winnerList;
    }

    public void setWinnerList(List<Long> winnerList) {
        this.winnerList = winnerList;
    }

    public Long getBankerId() {
        return bankerId;
    }

    public void setBankerId(Long bankerId) {
        this.bankerId = bankerId;
    }

    public List<Map<Long, Long>> getKillInfo() {
        return killInfo;
    }

    public GameResultHitGoldFlower setKillInfo(List<Map<Long, Long>> killInfo) {
        this.killInfo = killInfo;
        return this;
    }
}
