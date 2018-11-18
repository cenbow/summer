package com.code.server.game.mahjong.logic;

import com.code.server.game.mahjong.response.OperateReqResp;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by sunxianping on 2018/4/9.
 */
public class GameInfoHS extends GameInfoNew {

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
        this.users.addAll(users);
        this.room = room;
        this.cardSize = 13;
        this.playerSize = room.getPersonNumber();
        //不带风
        if (!PlayerCardsInfoMj.isHasMode(this.room.getMode(), 1)) {
            remainCards.removeAll(CardTypeUtil.FENG_CARD);
            remainCards.removeAll(CardTypeUtil.ZI_CARD);
        }
        fapai();
    }


    @Override
    protected void handleHuangzhuang(long userId) {
        turnResultToZeroOnHuangZhuang();
        sendResult(false, userId, null);
        noticeDissolutionResult();
        //通知所有玩家结束
        room.clearReadyStatus(true);

        //庄家换下个人
        if (room instanceof RoomInfo) {
            RoomInfo roomInfo = (RoomInfo) room;
            if (roomInfo.isChangeBankerAfterHuangZhuang()) {
                room.setBankerId(nextTurnId(room.getBankerId()));
            }

        }
    }

    protected void handleYiPaoDuoXiang() {

        List<Long> yipaoduoxiang = new ArrayList<>();

        //删除弃牌
        deleteDisCard(lastPlayUserId, disCard);
        this.waitingforList.forEach(waitDetail -> {
            if (waitDetail.isHu) {
                long uid = waitDetail.myUserId;
                yipaoduoxiang.add(uid);
                PlayerCardsInfoMj playerCardsInfoMj = playerCardsInfos.get(uid);
                playerCardsInfoMj.hu_dianpao(room, this, lastPlayUserId, disCard);
            }
        });

        //todo 下次的庄家
        this.room.setBankerId(lastPlayUserId);

        //回放
        OperateReqResp operateReqResp = new OperateReqResp();
        operateReqResp.setYipaoduoxiangUser(yipaoduoxiang);
        operateReqResp.setOperateType(OperateReqResp.type_yipaoduoxiang);
        operateReqResp.setIsMing(true);
        replay.getOperate().add(operateReqResp);

//        handleHu(playerCardsInfo);

        isAlreadyHu = true;
        sendResult(true, -1L, yipaoduoxiang);
        noticeDissolutionResult();
        room.clearReadyStatus(true);
    }


    /**
     * 设置庄家
     *
     * @param winnerId
     */
    public void setBanker(long winnerId) {

        room.setBankerId(winnerId);

    }




//    @Override
//    public int chupai(long userId, String card) {
//        int rtn = super.chupai(userId, card);
//        if (rtn != 0) {
//            return rtn;
//        }
//
//        //
//        PlayerCardsInfoMj playerCardsInfoMj = this.playerCardsInfos.get(userId);
//        if (playerCardsInfoMj != null) {
//
//            if (playerCardsInfoMj.tingWhatInfo.size() > 0) {
//                List<HuCardType> removeList = new ArrayList<>();
//                for (HuCardType huCardType : playerCardsInfoMj.tingWhatInfo) {
//                    if (!card.equals(huCardType.tingRemoveCard)) {
//                        removeList.add(huCardType);
//                    }
//                }
//
//                playerCardsInfoMj.tingWhatInfo.removeAll(removeList);
//
//
//            }
//            MsgSender.sendMsg2Player(ResponseType.SERVICE_TYPE_GAMELOGIC,"isContinueTing",playerCardsInfoMj.tingWhatInfo.size()>0, userId);
//        }
//        return 0;
//    }



}
