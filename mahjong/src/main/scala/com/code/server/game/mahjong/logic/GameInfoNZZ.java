package com.code.server.game.mahjong.logic;

import com.code.server.constant.response.ResponseVo;
import com.code.server.game.mahjong.response.*;
import com.code.server.game.room.kafka.MsgSender;

import java.util.*;

/**
 * Created by T420 on 2016/11/30.
 */

public class GameInfoNZZ extends GameInfo {

    private List<String> playCards = new ArrayList<>();
    private Set<Long> noCanHuList = new HashSet<>();//本轮不能胡的人
    private Map<Long,List<Integer>>  noCanPengList = new HashMap<>();//本轮不能碰牌的人
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
        fapai();
    }



    /**
     * 荒庄的处理
     *
     * @param userId
     */
    protected void  handleHuangzhuang(long userId) {
        //黄庄有杠，下家坐庄；黄庄无杠，继续坐庄
        boolean b = false;
        a:for (Long l: playerCardsInfos.keySet()) {
            if(playerCardsInfos.get(l).anGangType.size()>0){
                b=true;
                break a;
            }
            if(playerCardsInfos.get(l).mingGangType.keySet().size()>0){
                b=true;
                break a;
            }
        }
        if(b){
            room.setBankerId(nextTurnId(room.getBankerId()));
        }

        computeAllGang();
        sendResult(false, userId, null);
        noticeDissolutionResult();
        room.clearReadyStatus(true);
    }

    /**
     * 过牌
     * @param userId
     * @return
     */
    @Override
    public int guo(long userId) {

        PlayerCardsInfoNZZ guoPlayerCardsInfo = (PlayerCardsInfoNZZ)playerCardsInfos.get(userId);
        if(guoPlayerCardsInfo.isCanHu_dianpao(disCard)){//能胡点过的人，这一轮不能胡
            noCanHuList.add(userId);
        }
        if(guoPlayerCardsInfo.isCanPengAddThisCard(disCard)){//能碰点过的人，这一轮不能碰同一张牌
            List<Integer> pengCardList = new ArrayList<>();
            int cardType = CardTypeUtil.cardType.get(disCard);
            pengCardList.add(cardType);
            noCanPengList.put(userId,pengCardList);
        }

        if (isAlreadyHu) {
            return ErrorCode.CAN_NOT_GUO;
        }
        if (waitingforList.size() > 0) {

            List<WaitDetail> removeList = new ArrayList<>();
            for (WaitDetail waitDetail : waitingforList) {
                if (waitDetail.myUserId == userId) {
                    removeList.add(waitDetail);
                }
            }
            //todo 去掉可以操作集合
            resetCanBeOperate(playerCardsInfos.get(userId));
            waitingforList.removeAll(removeList);

            if (this.waitingforList.size() == 0) {
                //截杠胡
                if (jieGangHuCard != null) {
                    doGang_hand(playerCardsInfos.get(turnId),turnId,jieGangHuCard);
                    beJieGangUser = -1;
                    jieGangHuCard = null;
                } else {
                    //告诉全部点过
                    pushAllPass();
                    long nextId = nextTurnId(turnId);
                    //下个人摸牌
                    mopai(nextId,"过后摸牌");
                }
            } else {
                WaitDetail waitDetail = this.waitingforList.get(0);
                if (waitDetail.operate != -1) {
                    waitDetail.fire();
                    waitingforList.clear();
                }
            }

        }
        return 0;
    }

    /**
     * 出牌
     *
     * @param userId
     * @param card
     */
    @Override
    public int chupai(long userId, String card) {
        if (isAlreadyHu) {
            return ErrorCode.CAN_PLAYCARD_IS_HU;
        }
        System.out.println("出牌的人 = " + userId);
        //出牌的玩家
        PlayerCardsInfoNZZ chupaiPlayerCardsInfo = (PlayerCardsInfoNZZ)playerCardsInfos.get(userId);
        if (this.turnId != userId) {
            return ErrorCode.CAN_NOT_PLAYCARD;
        }
        if (!chupaiPlayerCardsInfo.checkPlayCard(card)) {
            return ErrorCode.CAN_NOT_PLAYCARD;
        }

        playCards.add(card);
        this.lastPlayUserId = userId;//上个出牌的人
        lastOperateUserId = userId;//上个操作的人
        //出的牌
        this.disCard = card;
        chupaiPlayerCardsInfo.chupai(card);


        //通知其他玩家出牌信息
        PlayCardResp playCardResp = new PlayCardResp();
        playCardResp.setUserId(userId);
        playCardResp.setCard(this.disCard);
        ResponseVo vo = new ResponseVo(ResponseType.SERVICE_TYPE_GAMELOGIC, ResponseType.METHOD_TYPE_PLAY_CARD, playCardResp);
        MsgSender.sendMsg2Player(vo, users);

        //回放 出牌
        OperateReqResp operateReqResp = new OperateReqResp();
        operateReqResp.setCard(card);
        operateReqResp.setUserId(userId);
        operateReqResp.setOperateType(OperateReqResp.type_play);
        replay.getOperate().add(operateReqResp);

        //其他人能做的操作
        for (Map.Entry<Long, PlayerCardsInfoMj> entry : playerCardsInfos.entrySet()) {
            OperateResp operateResp = new OperateResp();

            //其他玩家的处理 碰杠等 如果有加入等待列表(要等待这些玩家"过")
            if (userId != entry.getKey()) {
                //通知其他玩家出了什么牌 自己能有什么操作
                PlayerCardsInfoNZZ playerCardsInfo = (PlayerCardsInfoNZZ)entry.getValue();
                boolean isCanGang = playerCardsInfo.isCanGangAddThisCard(card);
                boolean isCanPeng;
                boolean isCanHu;
                //如果上一次操作为过，这一轮不能再碰和胡
                if(!noCanHuList.contains(playerCardsInfo.getUserId())){
                    isCanHu = playerCardsInfo.isCanHu_dianpao(card);
                }else{
                    isCanHu = false;
                }
                //同一张牌只能碰一次
                if(noCanPengList.keySet().contains(playerCardsInfo.getUserId()) && noCanPengList.get(playerCardsInfo.getUserId()).contains(CardTypeUtil.cardType.get(disCard))){
                    isCanPeng = false;
                }else{
                    isCanPeng = playerCardsInfo.isCanPengAddThisCard(card);
                }

                boolean isNext = nextTurnId(chupaiPlayerCardsInfo.getUserId()) == playerCardsInfo.getUserId();
                boolean isCanChi = isNext && playerCardsInfo.isHasChi(card);
                boolean isCanChiTing = playerCardsInfo.isCanChiTing(card);
                boolean isCanPengTing = playerCardsInfo.isCanPengTing(card);

                //设置返回结果
                operateResp.setCanBeOperate(isCanChi, isCanPeng, isCanGang, false, isCanHu, isCanChiTing, isCanPengTing);

                //设置自己能做的操作
                playerCardsInfo.setCanBeOperate(isCanChi, isCanPeng, isCanGang, false, isCanHu, isCanChiTing, isCanPengTing);

                boolean isWait = isCanGang || isCanPeng || isCanHu || isCanChi || isCanChiTing || isCanPengTing;
                if (isWait) {
                    this.waitingforList.add(new WaitDetail(entry.getKey(), isCanHu, isCanGang, isCanPeng, isCanChi, isCanChiTing, isCanPengTing));
                    add2WaitingList(entry.getKey(), isCanHu, isCanGang, isCanPeng, isCanChi, isCanChiTing, isCanPengTing);
                }
            }

            //可能的操作
            ResponseVo OperateVo = new ResponseVo(ResponseType.SERVICE_TYPE_GAMELOGIC, ResponseType.METHOD_TYPE_OPERATE, operateResp);
            MsgSender.sendMsg2Player(OperateVo, entry.getKey());
        }

        resetCanBeOperate(chupaiPlayerCardsInfo);


        System.out.println("======= waiting list : "+waitingforList.size());
        //如果等待列表为空 就轮到下个人摸牌
        if (this.waitingforList.size() == 0) {
            pushAllPass();
            long nextId = nextTurnId(turnId);
            mopai(nextId,"出牌");
        } else {
            //比较
            compare(waitingforList);
        }
        return 0;

    }


    /**
     * 听
     *
     * @param userId
     * @param card
     * @return
     */
    @Override
    public int ting(long userId, String card) {

        if (isAlreadyHu) {
            return ErrorCode.CAN_NOT_TING;
        }

        PlayerCardsInfoNZZ playerCardsInfo = (PlayerCardsInfoNZZ)playerCardsInfos.get(userId);
        if (playerCardsInfo == null) {
            return ErrorCode.USER_ERROR;
        }
        OperateReqResp operateReqResp = new OperateReqResp();
        operateReqResp.setOperateType(OperateReqResp.type_ting);
        operateReqResp.setUserId(userId);
        operateReqResp.setCard(card);
        ResponseVo vo = new ResponseVo(ResponseType.SERVICE_TYPE_GAMELOGIC, ResponseType.METHOD_TYPE_OTHER_OPERATE, operateReqResp);


        List<String> temp = new ArrayList<>();
        temp.addAll(playerCardsInfo.getCards());
        temp.remove(card);

        if (!playerCardsInfo.cards.contains(card)) {
            return ErrorCode.CAN_NOT_TING;
        }
        boolean isCan = playerCardsInfo.isCanTing(temp);//不多一张
        if (isCan) {
            playerCardsInfo.ting(card);
            //通知其他玩家听
            MsgSender.sendMsg2Player(vo, users);

            //回放
            replay.getOperate().add(operateReqResp);

            //通知其他玩家出牌信息
//            PlayCardResp playCardResp = new PlayCardResp();
//            playCardResp.setUserId(userId);
//            playCardResp.setCard(null);
//
//            ResponseVo chupaiVo = new ResponseVo(ResponseType.SERVICE_TYPE_GAMELOGIC, ResponseType.METHOD_TYPE_PLAY_CARD, playCardResp);
//            MsgSender.sendMsg2Player(chupaiVo, users);

            //出牌
            chuPai_ting(playerCardsInfo.getUserId(), card);
        } else {
            return ErrorCode.CAN_NOT_TING;
        }
        return 0;
    }

    /**
     * 摸牌
     *
     * @param userId
     */
    protected void mopai(long userId, String... wz) {
        //清楚胡牌和碰牌的限制
        noCanHuList.remove(userId);
        noCanPengList.remove(userId);

        System.err.println("摸牌===============================userId : " + userId);

        PlayerCardsInfoNZZ playerCardsInfo = (PlayerCardsInfoNZZ)playerCardsInfos.get(userId);

        if (isHuangzhuang(playerCardsInfo)) {
            handleHuangzhuang(userId);
            return;
        }


        if (playerCardsInfo.isMoreOneCard()) {
            if (wz.length > 0) {
                logger.info("======1操作后的摸牌 : " + wz[0]);
            }
            logger.info("userId : " + userId + "　===1 more one card 抓牌时多一张牌");
            logger.info("操作列表: " + playerCardsInfo.operateList.toString());
            logger.info("所有操作: " + userOperateList.toString());

        }

        //拿出一张
        String card = getMoPaiCard(playerCardsInfo);
        //有换牌需求
//        if (isTest && playerCardsInfo.nextNeedCard != -1) {
//            String needCard = getCardByTypeFromRemainCards(playerCardsInfo.nextNeedCard);
//            playerCardsInfo.nextNeedCard = -1;
//            if (needCard != null) {
//                card = needCard;
//                remainCards.remove(needCard);
//            } else {
//                card = remainCards.remove(0);
//            }
//        } else {
//            card = remainCards.remove(0);
//        }

        playerCardsInfo.mopai(card);
        //
        turnId = userId;
        this.lastMoPaiUserId = userId;
        lastOperateUserId = userId;
        this.catchCard = card;

        // 把摸到的牌 推给摸牌的玩家
        int remainSize = remainCards.size();
        for (long user : users) {
            GetCardResp getCardResp = new GetCardResp();
            getCardResp.setRemainNum(remainSize);
            getCardResp.setUserId(userId);
            if (user == userId) {
                getCardResp.setCard(card);
            }
            ResponseVo responseVo = new ResponseVo(ResponseType.SERVICE_TYPE_GAMELOGIC, ResponseType.METHOD_TYPE_GET_CARD, getCardResp);
            MsgSender.sendMsg2Player(responseVo, user);

            //能做的操作全置成不能
            PlayerCardsInfoNZZ other = (PlayerCardsInfoNZZ)playerCardsInfos.get(user);

            resetCanBeOperate(other);
        }


        boolean isCanGang = playerCardsInfo.isHasGang();
        boolean isCanTing = playerCardsInfo.isCanTing(playerCardsInfo.cards);//多一张
        boolean isCanHu = false;
        if(room.isHaveTing()){
            if(playerCardsInfo.isTing){
                isCanHu = playerCardsInfo.isCanHu_zimo(catchCard);
            }
        }else{
            isCanHu = playerCardsInfo.isCanHu_zimo(catchCard);
        }

        //能做的操作
        playerCardsInfo.setCanBeGang(isCanGang);
        playerCardsInfo.setCanBePeng(false);
        playerCardsInfo.setCanBeHu(isCanHu);
        playerCardsInfo.setCanBeTing(isCanTing);

        OperateResp resp = new OperateResp();
        resp.setIsCanGang(isCanGang);
        resp.setIsCanHu(isCanHu);
        resp.setIsCanTing(isCanTing);

        //回放 抓牌
        OperateReqResp operateReqResp = new OperateReqResp();
        operateReqResp.setCard(card);
        operateReqResp.setUserId(userId);
        operateReqResp.setOperateType(OperateReqResp.type_mopai);
        replay.getOperate().add(operateReqResp);

        //可能的操作
        ResponseVo OperateResponseVo = new ResponseVo(ResponseType.SERVICE_TYPE_GAMELOGIC, ResponseType.METHOD_TYPE_OPERATE, resp);
        MsgSender.sendMsg2Player(OperateResponseVo, userId);

    }



    //=====================================================





    private int chuPai_ting(long userId, String card) {
        //出牌的玩家
        PlayerCardsInfoNZZ chupaiPlayerCardsInfo = (PlayerCardsInfoNZZ)playerCardsInfos.get(userId);
        if (this.turnId != userId||isAlreadyHu) {
            return ErrorCode.CAN_NOT_PLAYCARD;
        }

        playCards.add(card);
        this.lastPlayUserId = userId;//上个出牌的人
        lastOperateUserId = userId;//上个操作的人
        //出的牌
        this.disCard = card;
        //chupaiPlayerCardsInfo.chupai(card);


        //通知其他玩家出牌信息
        PlayCardResp playCardResp = new PlayCardResp();
        playCardResp.setUserId(userId);
        playCardResp.setCard(null);
        ResponseVo vo = new ResponseVo(ResponseType.SERVICE_TYPE_GAMELOGIC, ResponseType.METHOD_TYPE_PLAY_CARD, playCardResp);
        MsgSender.sendMsg2Player(vo, users);


        //其他人能做的操作
        /*for (Map.Entry<Long, PlayerCardsInfoMj> entry : playerCardsInfos.entrySet()) {
            OperateResp operateResp = new OperateResp();

            //其他玩家的处理 碰杠等 如果有加入等待列表(要等待这些玩家"过")
            if (userId != entry.getKey()) {
                //通知其他玩家出了什么牌 自己能有什么操作
                PlayerCardsInfoNZZ playerCardsInfo = (PlayerCardsInfoNZZ)entry.getValue();
                boolean isCanGang = playerCardsInfo.isCanGangAddThisCard(card);
                boolean isCanPeng = playerCardsInfo.isCanPengAddThisCard(card);
                boolean isCanHu = playerCardsInfo.isCanHu_dianpao(card);
                boolean isNext = nextTurnId(chupaiPlayerCardsInfo.getUserId()) == playerCardsInfo.getUserId();
                boolean isCanChi = isNext && playerCardsInfo.isHasChi(card);
                boolean isCanChiTing = playerCardsInfo.isCanChiTing(card);
                boolean isCanPengTing = playerCardsInfo.isCanPengTing(card);
                //设置返回结果
                operateResp.setCanBeOperate(isCanChi, isCanPeng, isCanGang, false, isCanHu, isCanChiTing, isCanPengTing);

                //设置自己能做的操作
                playerCardsInfo.setCanBeOperate(isCanChi, isCanPeng, isCanGang, false, isCanHu, isCanChiTing, isCanPengTing);

                boolean isWait = isCanGang || isCanPeng || isCanHu || isCanChi || isCanChiTing || isCanPengTing;
                if (isWait) {
//                    this.waitingforList.add(new WaitDetail(entry.getKey(), isCanHu, isCanGang, isCanPeng, isCanChi, isCanChiTing, isCanPengTing));
                    add2WaitingList(entry.getKey(), isCanHu, isCanGang, isCanPeng, isCanChi, isCanChiTing, isCanPengTing);
                }
            }

            //可能的操作
            ResponseVo OperateVo = new ResponseVo(ResponseType.SERVICE_TYPE_GAMELOGIC, ResponseType.METHOD_TYPE_OPERATE, operateResp);
            MsgSender.sendMsg2Player(OperateVo, entry.getKey());
        }*/

        //其他人的操作 全是false 听牌后什么都不能操作
        for (Map.Entry<Long, PlayerCardsInfoMj> entry : playerCardsInfos.entrySet()) {
            PlayerCardsInfoMj pci = entry.getValue();
            pci.setCanBeGang(false);
            pci.setCanBePeng(false);
            pci.setCanBeHu(false);
            pci.setCanBeTing(false);

            OperateResp operateResp = new OperateResp();
            ResponseVo OperateVo = new ResponseVo(ResponseType.SERVICE_TYPE_GAMELOGIC, ResponseType.METHOD_TYPE_OPERATE, operateResp);
            MsgSender.sendMsg2Player(OperateVo, entry.getKey());
        }


        resetCanBeOperate(chupaiPlayerCardsInfo);


        //如果等待列表为空 就轮到下个人摸牌
        if (this.waitingforList.size() == 0) {
            pushAllPass();
            long nextId = nextTurnId(turnId);
            mopai(nextId,"听后出牌");
        } else {
            //比较
            compare(waitingforList);
        }
        return 0;

    }



    private void add2WaitingList(long userId, boolean isHu, boolean isGang, boolean isPeng, boolean isChi, boolean isChiTing, boolean isPengTing) {
        if (isHu) {
            this.waitingforList.add(new WaitDetail(userId, true, false, false, false, false, false));
        }
        if (isGang) {
            this.waitingforList.add(new WaitDetail(userId, false, true, false, false, false, false));
        }
        if (isPeng) {
            this.waitingforList.add(new WaitDetail(userId, false, false, true, false, false, false));
        }
        if (isChi) {
            this.waitingforList.add(new WaitDetail(userId, false, false, false, true, false, false));
        }
        if (isChiTing) {
            this.waitingforList.add(new WaitDetail(userId, false, false, false, false, true, false));
        }
        if (isPengTing) {
            this.waitingforList.add(new WaitDetail(userId, false, false, false, false, false, true));
        }

    }

    /**
     * 杠
     *
     * @param userId
     */
    public int gang(long userId, String card) {
        if (isAlreadyHu) {
            return ErrorCode.CAN_NOT_GANG;
        }
        PlayerCardsInfoNZZ playerCardsInfo = (PlayerCardsInfoNZZ)playerCardsInfos.get(userId);
        if (playerCardsInfo == null) {
            return ErrorCode.USER_ERROR;
        }
        OperateReqResp operateReqResp = new OperateReqResp();
        operateReqResp.setOperateType(OperateReqResp.type_gang);
        ResponseVo vo = new ResponseVo(ResponseType.SERVICE_TYPE_GAMELOGIC, ResponseType.METHOD_TYPE_OTHER_OPERATE, operateReqResp);

        if (lastOperateUserId == userId) {//杠手里的牌
            if ("".equals(card)) {
                System.err.println("=======================杠牌时 没传数据========");
            }
            if(!playerCardsInfo.isCanGangThisCard(card)){
                return ErrorCode.CAN_NOT_GANG;
            }
            int gangType = CardTypeUtil.getTypeByCard(card);
            boolean isMing = playerCardsInfo.getPengType().containsKey(gangType);
            //自摸不吃牌
            operateReqResp.setFromUserId(userId);
            operateReqResp.setUserId(userId);
            operateReqResp.setCard(card);
            operateReqResp.setIsMing(isMing);
            replay.getOperate().add(operateReqResp);
            //通知所有人有杠
            MsgSender.sendMsg2Player(vo, users);


            if (isMing) {

                for (Map.Entry<Long, PlayerCardsInfoMj> entry : playerCardsInfos.entrySet()) {
                    OperateResp operateResp = new OperateResp();

                    //其他玩家的处理 碰杠等 如果有加入等待列表(要等待这些玩家"过")
                    if (userId != entry.getKey()) {
                        //通知其他玩家出了什么牌 自己能有什么操作
                        PlayerCardsInfoNZZ playerOther = (PlayerCardsInfoNZZ)entry.getValue();
                        boolean isCanHu = playerOther.isCanHu_dianpao(card);
                        //设置返回结果
                        operateResp.setCanBeOperate(false, false, false, false, isCanHu, false, false);
                        //设置自己能做的操作
                        playerOther.setCanBeOperate(false, false, false, false, isCanHu, false, false);
                        boolean isWait = isCanHu;
                        if (isWait) {
                            add2WaitingList(entry.getKey(), isCanHu, false, false, false, false, false);
                        }
                    }

                    //可能的操作
                    ResponseVo OperateVo = new ResponseVo(ResponseType.SERVICE_TYPE_GAMELOGIC, ResponseType.METHOD_TYPE_OPERATE, operateResp);
                    MsgSender.sendMsg2Player(OperateVo, entry.getKey());
                }
            }

            if (this.waitingforList.size() == 0) {
                doGang_hand(playerCardsInfo, userId, card);
            } else {
                //排序
                compare(waitingforList);
                //出现截杠胡
                this.beJieGangUser = userId;
                this.jieGangHuCard = card;
            }



        } else {
            if (disCard == null) {
                return ErrorCode.CAN_NOT_GANG;
            }
            boolean isCanGang = playerCardsInfo.isCanGangAddThisCard(disCard);
            if (!isCanGang) {
                return ErrorCode.CAN_NOT_GANG;
            }
            handleWait(userId, WaitDetail.gangPoint);
            lastOperateUserId = userId;
        }


        return 0;


    }

    protected void doGang_hand(PlayerCardsInfoMj playerCardsInfo, long userId, String card){
        playerCardsInfo.gang_hand(room, this, userId, card);
        mopai(playerCardsInfo.getUserId(),"杠后摸牌");
        turnId = playerCardsInfo.getUserId();
        lastOperateUserId = playerCardsInfo.getUserId();
    }

    protected void doGang(PlayerCardsInfoMj playerCardsInfo, long userId) {
        OperateReqResp operateReqResp = new OperateReqResp();
        operateReqResp.setOperateType(OperateReqResp.type_gang);
        ResponseVo vo = new ResponseVo(ResponseType.SERVICE_TYPE_GAMELOGIC, ResponseType.METHOD_TYPE_OTHER_OPERATE, operateReqResp);
        //删除弃牌
        deleteDisCard(lastPlayUserId, disCard);

        playerCardsInfo.gang_discard(room, this, lastPlayUserId, disCard);
        operateReqResp.setFromUserId(lastPlayUserId);//谁出的牌

        operateReqResp.setUserId(userId);
        operateReqResp.setCard(disCard);
        operateReqResp.setIsMing(true);
        //通知所有人有杠
        MsgSender.sendMsg2Player(vo, users);

        mopai(userId,"杠后摸牌 点杠");
        turnId = userId;
        this.disCard = null;
        lastOperateUserId = userId;

        resetOtherOperate(userId);
    }

    /**
     * 碰牌
     *
     * @param userId
     * @return
     */
    public int peng(long userId) {
        if (isAlreadyHu) {
            return ErrorCode.CAN_NOT_PENG;
        }
        PlayerCardsInfoNZZ playerCardsInfo = (PlayerCardsInfoNZZ)playerCardsInfos.get(userId);
        if (playerCardsInfo == null) {
            return ErrorCode.USER_ERROR;
        }
        if (this.disCard == null) {
            return ErrorCode.CAN_NOT_PENG;
        }
        boolean isCan = playerCardsInfo.isCanPengAddThisCard(this.disCard);
        if (isCan) {
            handleWait(userId, WaitDetail.pengPoint);
        } else {
            return ErrorCode.CAN_NOT_PENG;
        }
        return 0;
    }

    protected void doPeng(PlayerCardsInfoMj playerCardsInfo, long userId){
        playerCardsInfo.peng(disCard, lastPlayUserId);
        lastOperateUserId = userId;

        //删除弃牌
        deleteDisCard(lastPlayUserId, disCard);


        //通知其他玩家

        OperateReqResp operateReqResp = new OperateReqResp();
        operateReqResp.setOperateType(OperateReqResp.type_peng);
        operateReqResp.setCard(disCard);
        operateReqResp.setFromUserId(lastPlayUserId);
        operateReqResp.setUserId(userId);

        //回放
        replay.getOperate().add(operateReqResp);

        //通知其他人
        ResponseVo vo = new ResponseVo(ResponseType.SERVICE_TYPE_GAMELOGIC, ResponseType.METHOD_TYPE_OTHER_OPERATE, operateReqResp);
        MsgSender.sendMsg2Player(vo, users);


        //碰完能听,杠,不能胡
        boolean isCanTing = playerCardsInfo.isCanTing(playerCardsInfo.cards);//多一张牌
        boolean isCanGang = playerCardsInfo.isHasGang();
        turnId = userId;
        // 通知客户端 操作
        OperateResp operateResp = new OperateResp();
        operateResp.setIsCanTing(isCanTing);
        operateResp.setIsCanGang(isCanGang);
        ResponseVo operateVo = new ResponseVo(ResponseType.SERVICE_TYPE_GAMELOGIC, ResponseType.METHOD_TYPE_OPERATE, operateResp);
        MsgSender.sendMsg2Player(operateVo,userId);
        this.disCard = null;
        //自己将能做的处理
        playerCardsInfo.canBePeng = false;
        playerCardsInfo.canBeTing = isCanTing;
        playerCardsInfo.canBeGang = isCanGang;
        playerCardsInfo.canBeHu = false;
        resetOtherOperate(userId);
    }





    private void pushAllPass(){
        Map map = new HashMap();
        map.put("isAllPass", true);
        map.put("lastPlayUser", lastPlayUserId);
        ResponseVo responseVo = new ResponseVo(ResponseType.SERVICE_TYPE_GAMELOGIC, ResponseType.METHOD_TYPE_ALL_PASS, map);
        MsgSender.sendMsg2Player(responseVo,users);
    }



    /**
     * 胡牌
     *
     * @param userId
     * @return
     */
    public int hu(long userId) {
        if (isAlreadyHu) {
            return ErrorCode.CAN_NOT_HU_ALREADY;
        }
        PlayerCardsInfoNZZ playerCardsInfo = (PlayerCardsInfoNZZ)playerCardsInfos.get(userId);
        if (playerCardsInfo == null) {
            return ErrorCode.USER_ERROR;
        }
        if (lastOperateUserId == userId) {//自摸
            if (playerCardsInfo.isCanHu_zimo(catchCard)) {
                playerCardsInfo.hu_zm(room, this, catchCard);
                handleHu(playerCardsInfo);
                setBanker(userId);
            } else {
                return ErrorCode.CAN_NOT_HU;
            }
        } else {

            if (this.disCard == null && jieGangHuCard == null) {
                return ErrorCode.CAN_NOT_HU;
            }
            String card = this.disCard;
            //
            if (jieGangHuCard != null) {
                card = jieGangHuCard;
            }

            if (playerCardsInfo.isCanHu_dianpao(card)) {
                handleWait(userId, WaitDetail.huPoint);
                setBanker(userId);
            } else {
                return ErrorCode.CAN_NOT_HU;
            }
        }


        return 0;

    }

    protected void doHu(PlayerCardsInfoMj playerCardsInfo, long userId){
        if (jieGangHuCard != null) {
            playerCardsInfo.hu_dianpao(room, this, beJieGangUser, jieGangHuCard);
            playerCardsInfos.get(beJieGangUser).cards.remove(jieGangHuCard);
            beJieGangUser = -1;
            jieGangHuCard = null;
        } else {
            //删除弃牌
            deleteDisCard(lastPlayUserId, disCard);
            playerCardsInfo.hu_dianpao(room, this, lastPlayUserId, disCard);
            this.disCard = null;
        }
        handleHu(playerCardsInfo);
    }


    public int chi(long userId, String one, String two) {
        if (isAlreadyHu) {
            return ErrorCode.CAN_NOT_CHI;
        }
        PlayerCardsInfoNZZ playerCardsInfo = (PlayerCardsInfoNZZ)playerCardsInfos.get(userId);
        if (disCard == null) {
            return ErrorCode.CAN_NOT_CHI;
        }
        boolean isCanChi = playerCardsInfo.isCanChiThisCard(disCard, one, two);
        if (!isCanChi) {
            return ErrorCode.CAN_NOT_CHI;
        }

        handleWait(userId, WaitDetail.chiPoint,one,two);
        return 0;
    }

    protected void doChi(PlayerCardsInfoMj playerCardsInfo, long userId, String one, String two) {
        deleteDisCard(lastPlayUserId, disCard);
        List<String> chiCards = new ArrayList<>();
        chiCards.add(one);
        chiCards.add(disCard);
        chiCards.add(two);
        OperateReqResp operateReqResp = new OperateReqResp();
        operateReqResp.setOperateType(OperateReqResp.type_chi);
        operateReqResp.setUserId(userId);
        operateReqResp.setChiCards(chiCards);
        operateReqResp.setFromUserId(lastPlayUserId);
        //回放
        replay.getOperate().add(operateReqResp);

        ResponseVo vo = new ResponseVo(ResponseType.SERVICE_TYPE_GAMELOGIC, ResponseType.METHOD_TYPE_OTHER_OPERATE, operateReqResp);
        //通知其他玩家听
        MsgSender.sendMsg2Player(vo, users);


        //吃
        playerCardsInfo.chi(disCard, one, two);

        //吃完能听,杠,不能胡
        boolean isCanTing = playerCardsInfo.isCanTing(playerCardsInfo.cards);//多一张牌
        boolean isCanGang = playerCardsInfo.isHasGang();
        turnId = userId;
        // 通知客户端 操作
        OperateResp operateResp = new OperateResp();
        operateResp.setIsCanTing(isCanTing);
        operateResp.setIsCanGang(isCanGang);
        ResponseVo operateVo = new ResponseVo(ResponseType.SERVICE_TYPE_GAMELOGIC, ResponseType.METHOD_TYPE_OPERATE, operateResp);
        MsgSender.sendMsg2Player(operateVo, userId);
        this.disCard = null;
        //自己将能做的处理
        playerCardsInfo.canBeChi = false;
        playerCardsInfo.canBeTing = isCanTing;
        playerCardsInfo.canBeGang = isCanGang;
        playerCardsInfo.canBeHu = false;

        resetOtherOperate(userId);
    }

    public int chiTing(long userId, String one, String two) {
        if (isAlreadyHu) {
            return ErrorCode.CAN_NOT_CHI;
        }
        PlayerCardsInfoNZZ playerCardsInfo = (PlayerCardsInfoNZZ)playerCardsInfos.get(userId);
        if (disCard == null) {
            return ErrorCode.CAN_NOT_CHI_TING;
        }
        boolean isCanChi = playerCardsInfo.isCanChiThisCard(disCard, one, two);
        if (!isCanChi) {
            return ErrorCode.CAN_NOT_CHI_TING;
        }

        handleWait(userId, WaitDetail.chiTingPoint,one,two);
        return 0;
    }

    protected void doChiTing(PlayerCardsInfoMj playerCardsInfo, long userId, String one, String two) {
        deleteDisCard(lastPlayUserId, disCard);
        List<String> chiCards = new ArrayList<>();
        chiCards.add(one);
        chiCards.add(disCard);
        chiCards.add(two);
        OperateReqResp operateReqResp = new OperateReqResp();
        operateReqResp.setOperateType(OperateReqResp.type_chi_ting);
        operateReqResp.setUserId(userId);
        operateReqResp.setChiCards(chiCards);
        operateReqResp.setFromUserId(lastPlayUserId);
        ResponseVo vo = new ResponseVo(ResponseType.SERVICE_TYPE_GAMELOGIC, ResponseType.METHOD_TYPE_OTHER_OPERATE, operateReqResp);
        //通知其他玩家听
        MsgSender.sendMsg2Player(vo, users);

        //吃
        playerCardsInfo.chi(disCard, one, two);

        //吃完只能听
        turnId = userId;
        // 通知客户端 操作
        OperateResp operateResp = new OperateResp();
        operateResp.setIsCanTing(false);//客户端特殊处理
        operateResp.setIsCanGang(false);
        ResponseVo operateVo = new ResponseVo(ResponseType.SERVICE_TYPE_GAMELOGIC, ResponseType.METHOD_TYPE_OPERATE, operateResp);
        MsgSender.sendMsg2Player(operateVo, userId);
        this.disCard = null;
        //自己将能做的处理
        playerCardsInfo.canBeChi = false;
        playerCardsInfo.canBeTing = true;
        playerCardsInfo.canBeChiTing = false;
        playerCardsInfo.canBeGang = false;

        resetOtherOperate(userId);
    }

    public int pengTing(long userId) {
        if (isAlreadyHu) {
            return ErrorCode.CAN_NOT_PENG_TING;
        }
        PlayerCardsInfoNZZ playerCardsInfo = (PlayerCardsInfoNZZ)playerCardsInfos.get(userId);

        if (disCard == null) {
            return ErrorCode.CAN_NOT_PENG_TING;
        }
        boolean isCan = playerCardsInfo.isCanPengTing(this.disCard);
        if (!isCan) {
            return ErrorCode.CAN_NOT_PENG_TING;
        }

        handleWait(userId, WaitDetail.pengTingPoint);


        return 0;
    }

    protected void doPengTing(PlayerCardsInfoMj playerCardsInfo, long userId) {
        playerCardsInfo.peng(disCard, lastPlayUserId);
        lastOperateUserId = userId;

        //删除弃牌
        deleteDisCard(lastPlayUserId, disCard);


        //通知其他玩家

        OperateReqResp operateReqResp = new OperateReqResp();
        operateReqResp.setOperateType(OperateReqResp.type_peng_ting);
        operateReqResp.setCard(disCard);
        operateReqResp.setFromUserId(lastPlayUserId);
        operateReqResp.setUserId(userId);

        //通知其他人
        ResponseVo vo = new ResponseVo(ResponseType.SERVICE_TYPE_GAMELOGIC, ResponseType.METHOD_TYPE_OTHER_OPERATE, operateReqResp);
        MsgSender.sendMsg2Player(vo, users);


        //碰完能听,杠,不能胡
        turnId = userId;
        // 通知客户端 操作
        OperateResp operateResp = new OperateResp();
        operateResp.setIsCanTing(false);//客户端特殊处理
        operateResp.setIsCanGang(false);
        ResponseVo operateVo = new ResponseVo(ResponseType.SERVICE_TYPE_GAMELOGIC, ResponseType.METHOD_TYPE_OPERATE, operateResp);
        MsgSender.sendMsg2Player(operateVo, userId);
        this.disCard = null;
        //自己将能做的处理
        playerCardsInfo.canBePeng = false;
        playerCardsInfo.canBeTing = true;
        playerCardsInfo.canBePengTing = false;
        playerCardsInfo.canBeGang = false;

        resetOtherOperate(userId);
    }

    protected void handleWait(long userId, int operateType, String... params) {
        List<WaitDetail> removeList = new ArrayList<>();
        for (WaitDetail waitDetail1 : waitingforList) {
            if (waitDetail1.myUserId == userId) {
                if (waitDetail1.getPoint() == operateType) {
                    waitDetail1.operate(this,operateType, params);
                } else {
                    removeList.add(waitDetail1);
                }
            }
        }
        waitingforList.removeAll(removeList);
        if (waitingforList.size() > 0) {
            WaitDetail waitDetail = waitingforList.get(0);
            if (waitDetail.operate != -1) {
                waitDetail.fire();
                waitingforList.clear();
            }

        } else {

        }

    }






    private void pushTing(int userId) {
        //通知其他玩家
        OperateReqResp operateReqResp = new OperateReqResp();
        operateReqResp.setOperateType(OperateReqResp.type_peng_ting);
        operateReqResp.setCard(disCard);
        operateReqResp.setFromUserId(lastPlayUserId);
        operateReqResp.setUserId(userId);
        //通知其他人
        ResponseVo vo = new ResponseVo(ResponseType.SERVICE_TYPE_GAMELOGIC, ResponseType.METHOD_TYPE_OTHER_OPERATE, operateReqResp);
        MsgSender.sendMsg2Player(vo, users);
    }
}
