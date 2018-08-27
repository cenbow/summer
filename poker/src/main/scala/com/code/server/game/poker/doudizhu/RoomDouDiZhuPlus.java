package com.code.server.game.poker.doudizhu;


import com.code.server.constant.data.DataManager;
import com.code.server.constant.exception.DataNotFoundException;
import com.code.server.constant.response.ErrorCode;
import com.code.server.constant.response.Notice;
import com.code.server.constant.response.NoticeReady;
import com.code.server.constant.response.ResponseVo;
import com.code.server.game.room.Game;
import com.code.server.game.room.kafka.MsgSender;
import com.code.server.game.room.service.RoomManager;
import com.code.server.redis.service.RedisManager;
import com.code.server.util.timer.GameTimer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Clark on 2017/8/7.Long
 */
public class RoomDouDiZhuPlus extends RoomDouDiZhu {

    public static final int PERSONNUM = 3;

    public static final Map<Integer,Integer> needsMoney = new HashMap<>();
    public static final Map<Integer,Integer> usesMoney = new HashMap<>();

    static {
        needsMoney.put(10,60);
        needsMoney.put(50,300);
        needsMoney.put(100,1000);

        usesMoney.put(10,2);
        usesMoney.put(50,4);
        usesMoney.put(100,6);
    }


    public int joinRoom(long userId, boolean isJoin) {

        if (userId == 0) {
            return ErrorCode.JOIN_ROOM_USERID_IS_0;
        }
        if (this.users.contains(userId)) {
            return ErrorCode.CANNOT_CREATE_ROOM_USER_HAS_IN_ROOM;
        }
        if (this.users.size() >= this.personNumber) {
            return ErrorCode.CANNOT_JOIN_ROOM_IS_FULL;

        }
        if (RedisManager.getUserRedisService().getRoomId(userId) != null) {
            return ErrorCode.CANNOT_CREATE_ROOM_USER_HAS_IN_ROOM;
        }
        if (!isCanJoinCheckMoney(userId)) {
            return ErrorCode.CANNOT_JOIN_ROOM_NO_MONEY;
        }


        pushScoreChange();
        if (isJoin) {
            roomAddUser(userId);
            //加进玩家-房间映射表
            noticeJoinRoom(userId);
        }

        return 0;
    }

    public int getReady(long userId) {
        if (RedisManager.getUserRedisService().getUserMoney(userId) < needsMoney.get(goldRoomType)) {
            quitRoom(userId);
            return ErrorCode.CANNOT_JOIN_ROOM_NO_MONEY;
        }
        if (!this.users.contains(userId)) {
            return ErrorCode.CANNOT_FIND_THIS_USER;
        }
        if (isInGame) {
            return ErrorCode.CANNOT_FIND_THIS_USER;
        }

        this.userStatus.put(userId, STATUS_READY);

        int readyNum = 0;
        for (Map.Entry<Long, Integer> entry : this.userStatus.entrySet()) {
            if (entry.getValue() == STATUS_READY) {
                readyNum += 1;
            }
        }

        pushScoreChange();

        //通知客户端谁是否准备
        Map<String, Integer> userStatus = new HashMap<>();
        for (Long i : this.userStatus.keySet()) {
            userStatus.put(i + "", this.userStatus.get(i));
        }
        NoticeReady noticeReady = new NoticeReady();
        noticeReady.setUserStatus(userStatus);
        MsgSender.sendMsg2Player(new ResponseVo("roomService", "noticeReady", noticeReady), this.users);

        //开始游戏
        if (readyNum >= personNumber) {
            startGame();
        }
        MsgSender.sendMsg2Player(new ResponseVo("roomService", "getReady", 0), userId);
        return 0;
    }


    @Override
    protected Game getGameInstance() {
        return new GameDouDiZhuPlus();
    }


    public void startGame() {
        this.isInGame = true;
        Game game = getGameInstance();
        this.game = game;
        //游戏开始 代建房 去除定时解散
        if(!isOpen && !this.isCreaterJoin()){
            GameTimer.removeNode(prepareRoomTimerNode);
        }
        game.startGame(users, this);
        this.isOpen = true;
        spendMoney();
        pushScoreChange();
    }


    public void spendMoney() {
        this.users.forEach(userId -> {
            RedisManager.getUserRedisService().addUserMoney(userId, - usesMoney.get(this.goldRoomType));
            if (isAddGold()) RedisManager.addGold(userId, usesMoney.get(goldRoomType) / 10);
        });
    }


    protected boolean isCanJoinCheckMoney(long userId) {

        if (RedisManager.getUserRedisService().getUserMoney(userId) < needsMoney.get(this.goldRoomType)) {
            return false;
        }
        return true;
    }


    public static Map<Integer, Integer> getNeedsMoney() {
        return needsMoney;
    }

    public static Map<Integer, Integer> getUsesMoney() {
        return usesMoney;
    }

    public void init(int gameNumber, int multiple) throws DataNotFoundException {
        this.goldRoomType = multiple;
        this.multiple = -1;
        this.gameNumber = gameNumber;
        this.isInGame = false;
        this.maxZhaCount = 9999;
        this.createNeedMoney = this.getNeedMoney();
        this.isAddGold = DataManager.data.getRoomDataMap().get(this.gameType).getIsAddGold() == 1;
    }

    public int getNeedMoney() {
       return (int)this.goldRoomType;
    }


    public void pushScoreChange() {
        Map<Long, Double> userMoneys = new HashMap<>();
        for (Long l: users) {
            userMoneys.put(l,RedisManager.getUserRedisService().getUserMoney(l));
        }
        MsgSender.sendMsg2Player(new ResponseVo("gameService", "scoreChange", userMoneys), this.getUsers());
    }

    public int quitRoom(long userId) {
        if (!this.users.contains(userId)) {
            return ErrorCode.CANNOT_QUIT_ROOM_NOT_EXIST;

        }
        if (isInGame) {
            return ErrorCode.CANNOT_QUIT_ROOM_IS_IN_GAME;
        }

        List<Long> noticeList = new ArrayList<>();
        noticeList.addAll(this.getUsers());

        //删除玩家房间映射关系
        roomRemoveUser(userId);
        if (this.createUser == userId) {//房主解散

            Notice n = new Notice();
            n.setMessage("roomNum " + this.getRoomId() + " :has destroy success!");
            MsgSender.sendMsg2Player(new ResponseVo("roomService", "destroyRoom", n), noticeList);

            RoomManager.removeRoom(this.roomId);
        }
        noticeQuitRoom(userId);
        return 0;
    }









}