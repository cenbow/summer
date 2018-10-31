package com.code.server.game.mahjong.logic;

import com.code.server.constant.response.ResponseVo;
import com.code.server.game.mahjong.response.OperateReqResp;
import com.code.server.game.mahjong.response.PlayerCardsResp;
import com.code.server.game.mahjong.response.ResponseType;
import com.code.server.game.mahjong.response.ResultResp;
import com.code.server.game.room.kafka.MsgSender;

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


    /**
     * 发送结果
     *
     * @param isHasWinner
     * @param winnerId
     * @param yipaoduoxiang
     */
    protected void sendResult(boolean isHasWinner, Long winnerId, List<Long> yipaoduoxiang) {
        ResultResp result = new ResultResp();
        ResponseVo vo = new ResponseVo(ResponseType.SERVICE_TYPE_GAMELOGIC, ResponseType.METHOD_TYPE_RESULT, result);

        if (isHasWinner) {
            if (yipaoduoxiang == null) {
                result.setWinnerId(winnerId);
            } else {
                result.setYipaoduoxiang(yipaoduoxiang);
            }
            result.setBaoCard(baoCard);
        }
        List<PlayerCardsResp> list = new ArrayList<>();
        for (PlayerCardsInfoMj info : playerCardsInfos.values()) {
            PlayerCardsResp resp = new PlayerCardsResp(info);
            resp.setAllScore(room.getUserScores().get(info.getUserId()));
            list.add(resp);
        }
        result.setUserInfos(list);
        result.setLaZhuang(this.room.laZhuang);
        result.setLaZhuangStatus(this.room.laZhuangStatus);
        result.setYu("" + PlayerCardsInfoHS.getYuNum(this.room.getMode()));
        MsgSender.sendMsg2Player(vo, users);


        //回放
        replay.setResult(result);
        //生成记录
        genRecord();
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


    @Override
    protected void resetCanBeOperate(PlayerCardsInfoMj playerCardsInfo) {
        playerCardsInfo.setCanBeChi(false);
        playerCardsInfo.setCanBeGang(false);
        playerCardsInfo.setCanBePeng(false);
        playerCardsInfo.setCanBeHu(false);
        playerCardsInfo.setCanBeTing(false);
        playerCardsInfo.setCanBeChiTing(false);
        playerCardsInfo.setCanBePengTing(false);
        playerCardsInfo.setCanBeXuanfeng(false);
    }
}
