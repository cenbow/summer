package com.code.server.login.service;

import com.code.server.constant.club.ClubMember;
import com.code.server.constant.club.RoomInstance;
import com.code.server.constant.club.RoomModel;
import com.code.server.constant.data.DataManager;
import com.code.server.constant.data.StaticDataProto;
import com.code.server.constant.game.UserBean;
import com.code.server.constant.kafka.IKafaTopic;
import com.code.server.constant.kafka.KafkaMsgKey;
import com.code.server.constant.response.ClubVo;
import com.code.server.constant.response.ErrorCode;
import com.code.server.constant.response.ResponseVo;
import com.code.server.constant.response.RoomInstanceVo;
import com.code.server.db.Service.ClubService;
import com.code.server.db.model.Club;
import com.code.server.kafka.MsgProducer;
import com.code.server.login.config.ServerConfig;
import com.code.server.redis.service.RedisManager;
import com.code.server.util.IdWorker;
import com.code.server.util.JsonUtil;
import com.code.server.util.SpringUtil;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by sunxianping on 2018/1/15.
 */
@Service
public class GameClubService {

    @Autowired
    private ClubService clubService;

    @Autowired
    private MsgProducer kafkaMsgProducer;

    private static final int NEED_MONEY = 500;
    private static final int JOIN_LIMIT = 5;
    private static final int ROOM_LIMIT = 3;

    /**
     * 查看俱乐部
     * @param msgKey
     * @param userId
     * @return
     */
    public int lookClub(KafkaMsgKey msgKey, long userId) {
        List<ClubVo> list = new ArrayList<>();

        List<String> clubs = ClubManager.getInstance().getUserClubs(userId);

        for (String clubId : clubs) {
            list.add(getClubVo_simple(ClubManager.getInstance().getClubById(clubId)));
        }

        sendMsg(msgKey, new ResponseVo("clubService", "lookClub", list));
        return 0;
    }

    /**
     * 获得俱乐部信息
     * @param msgKey
     * @param userId
     * @param clubId
     * @return
     */
    public int getClubInfo(KafkaMsgKey msgKey, long userId, String clubId) {
        Club club = ClubManager.getInstance().getClubById(clubId);

        if (club == null) {
            return ErrorCode.CLUB_NO_THIS;
        }


        boolean isPresident = club.getPresident() == userId;
        ClubVo clubVo = getClubVo_simple(club);
        clubVo.getRoomModels().addAll(club.getClubInfo().getRoomModels());
        //玩家在线情况
        clubVo.getMember().addAll(club.getClubInfo().getMember().values());
        clubVo.getMember().forEach(clubMember -> {
            String gateId = RedisManager.getUserRedisService().getGateId(clubMember.getUserId());
            boolean online = gateId != null;
            clubMember.setOnline(online);
        });

        club.getClubInfo().getRoomInstance().values().forEach(roomInstance -> {
            if (roomInstance.getRoomId() != null) {
                clubVo.getRoomInstance().add(getRoomInstanceVo(roomInstance));
            }
        });

        //房间情况
        if (isPresident) {
            clubVo.getApplyList().addAll(club.getClubInfo().getApplyList());
        }


        //todo 初始化数据
        sendMsg(msgKey, new ResponseVo("clubService", "getClubInfo", clubVo));
        return 0;
    }

    /**
     * 获得房间实例vo
     * @param roomInstance
     * @return
     */
    private RoomInstanceVo getRoomInstanceVo(RoomInstance roomInstance) {
        RoomInstanceVo vo = new RoomInstanceVo();
        vo.setClubRoomModel(roomInstance.getRoomModelId());
        vo.setRoomId(roomInstance.getRoomId());
        int num = 0;
        if (roomInstance.getRoomId() != null) {

            num = RedisManager.getRoomRedisService().getUsers(roomInstance.getRoomId()).size();
        }
        vo.setNum(num);
        return vo;
    }

    /**
     * 创建俱乐部
     *
     * @param msgKey
     * @param userId
     * @param clubName
     * @param wx
     * @param area
     * @param desc
     * @return
     */
    public int createClub(KafkaMsgKey msgKey, long userId, String clubName, String wx, String area, String desc) {

        //钱是否够
        double money = RedisManager.getUserRedisService().getUserMoney(userId);
        if (money < NEED_MONEY) {
            return ErrorCode.CLUB_CANNOT_MONEY;
        }
        //多于5个俱乐部 不可以创建
        int num = ClubManager.getInstance().getUserClubNum(userId);
        if (num >= JOIN_LIMIT) {
            return ErrorCode.CLUB_CANNOT_NUM;
        }

        //人减钱
        RedisManager.getUserRedisService().addUserMoney(userId, -NEED_MONEY);

        Club club = new Club();
        UserBean userBean = RedisManager.getUserRedisService().getUserBean(userId);
        //id
        club.setId(ClubManager.getInstance().getClubId());
        club.setPresident(userId);
        club.setName(clubName);
        club.setPresidentName(userBean.getUsername());
        club.setPresidentWx(wx);
        club.setArea(area);
        club.setMoney(NEED_MONEY);
        club.setClubDesc(desc);

        clubAddMember(club, userBean);

        clubService.getClubDao().save(club);

        ClubManager.getInstance().getClubMap().put(club.getId(), club);


        ResponseVo vo = new ResponseVo("clubService", "createClub", club);
        sendMsg(msgKey, vo);
        return 0;
    }


    /**
     * 设置俱乐部
     * @param msgKey
     * @param userId
     * @param clubId
     * @param clubName
     * @param wx
     * @param area
     * @param desc
     * @return
     */
    public int setClub(KafkaMsgKey msgKey, long userId, String clubId, String clubName, String wx, String area, String desc){
        Club club = ClubManager.getInstance().getClubById(clubId);
        if (club == null) {
            return ErrorCode.CLUB_NO_THIS;
        }

        if (userId != club.getPresident()) {
            return ErrorCode.CLUB_CANNOT_NO_PRESIDENT;
        }
        club.setName(clubName).setPresidentWx(wx).setArea(area).setClubDesc(desc);

        sendMsg(msgKey, new ResponseVo("clubService", "setClub", getClubVo_simple(club)));
        return 0;
    }

    /**
     * mark 用户
     * @param msgKey
     * @param userId
     * @param clubId
     * @param markUser
     * @param mark
     * @return
     */
    public int markUser(KafkaMsgKey msgKey, long userId, String clubId,long markUser, String mark){
        Club club = ClubManager.getInstance().getClubById(clubId);
        if (club == null) {
            return ErrorCode.CLUB_NO_THIS;
        }

        if (userId != club.getPresident()) {
            return ErrorCode.CLUB_CANNOT_NO_PRESIDENT;
        }
        ClubMember clubMember = club.getClubInfo().getMember().get("" + markUser);
        clubMember.setMark(mark);

        sendMsg(msgKey, new ResponseVo("clubService", "markUser", "ok"));

        return 0;
    }

    /**
     * 解散
     * @param msgKey
     * @param userId
     * @param clubId
     * @return
     */
    public int dissolve(KafkaMsgKey msgKey, long userId, String clubId) {
        Club club = ClubManager.getInstance().getClubById(clubId);
        if (club == null) {
            return ErrorCode.CLUB_NO_THIS;
        }

        if (userId != club.getPresident()) {
            return ErrorCode.CLUB_CANNOT_NO_PRESIDENT;
        }


        //玩家删除id
        List<String> removeList = new ArrayList<>();
        removeList.addAll(club.getClubInfo().getMember().keySet());

        for (String uid : removeList) {
            clubRemoveMember(club, Long.valueOf(uid));

        }

        //删除club
        ClubManager.getInstance().getClubMap().remove(clubId);

        //把钱加回去
        RedisManager.getUserRedisService().addUserMoney(userId, club.getMoney());

        sendMsg(msgKey, new ResponseVo("clubService", "dissolve", club));
        return 0;
    }

    /**
     * 是否有此俱乐部
     * @param msgKey
     * @param userId
     * @param clubId
     * @return
     */
    public int isHasClub(KafkaMsgKey msgKey, long userId, String clubId) {
        Map<String, Object> result = new HashMap<>();
        result.put("isHas", ClubManager.getInstance().getClubMap().containsKey(clubId));
        sendMsg(msgKey, new ResponseVo("clubService", "isHasClub", result));
        return 0;
    }

    /**
     * 加入俱乐部
     *
     * @param msgKey
     * @param userId
     * @param clubId
     * @return
     */
    public int joinClub(KafkaMsgKey msgKey, long userId, String clubId, String mark) {
        Club club = ClubManager.getInstance().getClubById(clubId);
        if (club == null) {
            return ErrorCode.CLUB_NO_THIS;
        }
        //自己加入了几个俱乐部
        List<String> joinList = ClubManager.getInstance().getUserClubs(userId);
        if (joinList.size() >= JOIN_LIMIT) {
            return ErrorCode.CLUB_CANNOT_NUM;
        }
        if (joinList.contains(clubId)) {
            return ErrorCode.CLUB_CANNOT_JOIN;
        }

        //加入申请列表
        if (isInApplyList(club, userId)) {
            return ErrorCode.CLUB_CANNOT_JOIN;
        }
        UserBean userBean = RedisManager.getUserRedisService().getUserBean(userId);

        String name = userBean.getUsername();
        String image = userBean.getImage();
        ClubMember apply = new ClubMember().setTime(System.currentTimeMillis()).setUserId(userId).setMark(mark).setName(name).setImage(image);
        club.getClubInfo().getApplyList().add(apply);

        Map<String, Object> result = new HashMap<>();
        ResponseVo vo = new ResponseVo("clubService", "joinClub", result);
        sendMsg(msgKey, vo);

        return 0;
    }


    /**
     * 退出俱乐部
     *
     * @param msgKey
     * @param userId
     * @param clubId
     * @return
     */
    public int quitClub(KafkaMsgKey msgKey, long userId, String clubId) {
        Club club = ClubManager.getInstance().getClubById(clubId);
        if (club == null) {
            return ErrorCode.CLUB_NO_THIS;
        }
        //todo

        //删除
        clubRemoveMember(club, userId);
        ResponseVo vo = new ResponseVo("clubService", "quitClub", "ok");
        sendMsg(msgKey, vo);
        return 0;
    }


    /**
     * 同意加入俱乐部
     * @param msgKey
     * @param userId
     * @param clubId
     * @param agreeId
     * @param isAgree
     * @return
     */
    public int agree(KafkaMsgKey msgKey, long userId, String clubId, long agreeId, boolean isAgree) {
        Club club = ClubManager.getInstance().getClubById(clubId);
        if (club == null) {
            return ErrorCode.CLUB_NO_THIS;
        }

        if (club.getPresident() != userId) {
            return ErrorCode.CLUB_NOT_PRESIDENT;
        }

        if (ClubManager.getInstance().getUserClubNum(agreeId) >= JOIN_LIMIT) {
            return ErrorCode.CLUB_CANNOT_JOIN;
        }
        //加入俱乐部
        ClubMember apply = getApply(club, agreeId);
        if (isAgree) {
            if (apply != null) {

                clubAddMember(club, apply);
            }
            String name = apply == null ? "" : apply.getName();
        }
        //删除申请列表
//        removeUserFromApplyList(club, agreeId);
        club.getClubInfo().getApplyList().remove(apply);
        sendMsg(msgKey, new ResponseVo("clubService", "agree", "ok"));

        return 0;
    }


    /**
     * 充值
     * @param msgKey
     * @param userId
     * @param clubId
     * @param money
     * @return
     */
    public int charge(KafkaMsgKey msgKey, long userId, String clubId, int money) {
        Club club = ClubManager.getInstance().getClubById(clubId);
        if (club == null) {
            return ErrorCode.CLUB_NO_THIS;
        }

        if (club.getPresident() != userId) {
            return ErrorCode.CLUB_NOT_PRESIDENT;
        }

        if(money <=0){
            return ErrorCode.REQUEST_PARAM_ERROR;
        }
        if (RedisManager.getUserRedisService().getUserMoney(userId) < money) {
            return ErrorCode.NOT_HAVE_MORE_MONEY;
        }
        //加钱
        club.setMoney(club.getMoney() + money);
        RedisManager.getUserRedisService().addUserMoney(userId, -money);
        sendMsg(msgKey, new ResponseVo("clubService", "charge", "ok"));
        return 0;
    }


    private void initRoomData(){
        //加载数据
        if (DataManager.data == null) {

            ServerConfig serverConfig = SpringUtil.getBean(ServerConfig.class);
            try {
                DataManager.initData(serverConfig.getDataFile());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    /**
     * 创建房间model
     * @param createCommand
     * @param userId
     * @param clubId
     * @param gameType
     * @param gameNumber
     * @param desc
     * @return
     */
    public int createRoomModel(KafkaMsgKey msgKey, long userId, String clubId, String createCommand,String gameType, int gameNumber,String desc){
        Club club = ClubManager.getInstance().getClubById(clubId);
        if (club == null) {
            return ErrorCode.CLUB_NO_THIS;
        }

        if (club.getPresident() != userId) {
            return ErrorCode.CLUB_NOT_PRESIDENT;
        }
        if(club.getClubInfo().getRoomModels().size() >=ROOM_LIMIT){
            return ErrorCode.CLUB_NOT_MODEL_LIMIT;
        }


        //初始化 房间数据
        initRoomData();

        StaticDataProto.RoomData roomData = DataManager.data.getRoomDataMap().get(gameType);
        if (roomData == null || !roomData.getMoneyMap().containsKey(gameNumber)) {
            return ErrorCode.REQUEST_PARAM_ERROR;
        }


        RoomModel roomModel = new RoomModel();
        String id = ""+IdWorker.getDefaultInstance().nextId();
        roomModel.setId(id);
        JsonNode jsonNode = JsonUtil.readTree(createCommand);
        String serviceName = jsonNode.path("service").asText();
        //设置创建命令
        createCommand = setRoomModelCommand(createCommand, clubId, id);
        roomModel.setCreateCommand(createCommand);
        roomModel.setDesc(desc);
        roomModel.setTime(System.currentTimeMillis());
        roomModel.setMoney(roomData.getMoneyMap().get(gameNumber));
        roomModel.setServiceName(serviceName);

        club.getClubInfo().getRoomModels().add(roomModel);

        sendMsg(msgKey, new ResponseVo("clubService", "createRoomModel", roomModel));
        //实例化房间
        initRoomInstance(club);
        return 0;
    }

    /**
     * 删除房间模式
     * @param msgKey
     * @param userId
     * @param clubId
     * @param roomModelId
     * @return
     */
    public int removeRoomModel(KafkaMsgKey msgKey, long userId, String clubId,String roomModelId){
        Club club = ClubManager.getInstance().getClubById(clubId);
        if (club == null) {
            return ErrorCode.CLUB_NO_THIS;
        }

        if (club.getPresident() != userId) {
            return ErrorCode.CLUB_NOT_PRESIDENT;
        }
        RoomModel roomModel = getRoomModel(club, roomModelId);
        if (roomModel != null) {
            club.getClubInfo().getRoomModels().remove(roomModel);
        }
        sendMsg(msgKey, new ResponseVo("clubService", "removeRoomModel", "ok"));
        return 0;
    }


    public int setRoomModel(KafkaMsgKey msgKey, long userId, String clubId,String roomModelId, String createCommand,String gameType, int gameNumber,String desc){
        Club club = ClubManager.getInstance().getClubById(clubId);
        if (club == null) {
            return ErrorCode.CLUB_NO_THIS;
        }

        if (club.getPresident() != userId) {
            return ErrorCode.CLUB_NOT_PRESIDENT;
        }
        if(club.getClubInfo().getRoomModels().size() >=ROOM_LIMIT){
            return ErrorCode.CLUB_NOT_MODEL_LIMIT;
        }


        //初始化 房间数据
        initRoomData();

        StaticDataProto.RoomData roomData = DataManager.data.getRoomDataMap().get(gameType);
        if (roomData == null || !roomData.getMoneyMap().containsKey(gameNumber)) {
            return ErrorCode.REQUEST_PARAM_ERROR;
        }




        JsonNode jsonNode = JsonUtil.readTree(createCommand);
        String serviceName = jsonNode.path("service").asText();
        //设置创建命令

        RoomModel roomModel = getRoomModel(club, roomModelId);
        createCommand = setRoomModelCommand(createCommand, clubId, roomModel.getId());
        roomModel.setCreateCommand(createCommand);
        roomModel.setDesc(desc);
        roomModel.setTime(System.currentTimeMillis());
        roomModel.setMoney(roomData.getMoneyMap().get(gameNumber));
        roomModel.setServiceName(serviceName);


        sendMsg(msgKey, new ResponseVo("clubService", "setRoomModel", roomModel));
        //实例化房间
        initRoomInstance(club);
        return 0;
    }


    public int clubRoomSetId(String clubId, String clubModelId,String roomId) {
        Club club = ClubManager.getInstance().getClubById(clubId);
        if (club != null) {
            RoomInstance roomInstance = club.getClubInfo().getRoomInstance().get(clubModelId);
            if (roomInstance != null) {
                roomInstance.setRoomId(roomId);
            }
        }
        return 0;
    }
    /**
     * 拿到roomModel
     * @param club
     * @param roomModelId
     * @return
     */
    public static RoomModel getRoomModel(Club club, String roomModelId){
        RoomModel roomModel = null;
        for (RoomModel rm : club.getClubInfo().getRoomModels()) {
            if (roomModelId.equals(rm.getId())) {
                roomModel = rm;
            }
        }
        return roomModel;
    }

    /**
     * 初始化俱乐部
     * @param club
     */
    public static void initRoomInstance(Club club){
        System.out.println("init------------------");

        synchronized (club) {

            //清理房间状态 如果房间已不存在 则去掉roomId (比如逻辑服务器重启)
            List<String> removeList = new ArrayList<>();
            for (Map.Entry<String,RoomInstance> entry : club.getClubInfo().getRoomInstance().entrySet()) {
                if (entry.getValue().getRoomId()!=  null && RedisManager.getRoomRedisService().getServerId(entry.getValue().getRoomId()) == null) {
                    removeList.add(entry.getKey());
                }
            }
            removeList.forEach(modelKey->club.getClubInfo().getRoomInstance().remove(modelKey));


            if(club.getClubInfo().getRoomInstance().size() < ROOM_LIMIT) {
                //创建
                for(RoomModel roomModel : club.getClubInfo().getRoomModels()){
                    //没有这个类型的房间 && 钱够
                    if(!club.getClubInfo().getRoomInstance().containsKey(roomModel.getId()) && club.getMoney() >= roomModel.getMoney()){
                        //创建房间
                        RoomInstance roomInstance = new RoomInstance();
                        roomInstance.setRoomModelId(roomModel.getId());
                        //放进 房间实例 列表
                        club.getClubInfo().getRoomInstance().put(roomInstance.getRoomModelId(), roomInstance);

                        //发消息创建房间
                        sendMsgForCreateRoom(roomModel.getServiceName(), roomModel.getCreateCommand());
                        //减钱
                        int moneyNow = club.getMoney() - roomModel.getMoney();
                        club.setMoney(moneyNow);
                    }
                }

            }
        }
    }



    private static int getServerIdByServiceName(String serviceName){
        if("mahjongRoomService".equals(serviceName)){
            return 0;
        } else if ("pokerRoomService".equals(serviceName)) {
            return 1;
        } else {
            return 0;
        }
    }
    private static void sendMsgForCreateRoom(String serviceName,String createCommand){
        System.out.println("创建---");
        MsgProducer msgProducer = SpringUtil.getBean(MsgProducer.class);
        int serverId = getServerIdByServiceName(serviceName);
        KafkaMsgKey msgKey = new KafkaMsgKey();
        msgKey.setUserId(0);

        msgProducer.send2Partition(serviceName, serverId, JsonUtil.toJson(msgKey), createCommand);

    }
    /**
     * 修改房间创建命令
     * @param createCommand
     * @param clubId
     * @param modelId
     * @return
     */
    private String setRoomModelCommand(String createCommand, String clubId, String modelId){
        Map<String,Object> map = JsonUtil.readValue(createCommand, Map.class);
        Object pa = map.get("params");
        Map<String,Object> room = (Map<String,Object>)pa;
        room.put("clubId", clubId);
        room.put("clubRoomModel", modelId);
        map.put("params", room);
        return JsonUtil.toJson(map);
    }

    public static void main(String[] args) {
        String s = "{\"service\":\"pokerRoomService\",\"method\":\"createRoom\",\"params\":{\"gameType\":\"2\",\"gameNumber\":\"9\",\"maxMultiple\":\"-1\",\"roomType\":\"2\",\"isAA\":false,\"isJoin\":false}}";
       // setRoomModelCommand(s, "1","2");
    }


    /**
     * 获得俱乐部简要信息
     * @param club
     * @return
     */
    private ClubVo getClubVo_simple(Club club) {
        if (club == null) {
            return new ClubVo();
        }
        ClubVo clubVo = new ClubVo();
        clubVo.setId(club.getId());
        clubVo.setPresident(club.getPresident());
        clubVo.setName(club.getName());
        clubVo.setPresidentName(club.getPresidentName());
        clubVo.setNum(club.getClubInfo().getMember().size());
        clubVo.setMoney(club.getMoney());
        clubVo.setArea(club.getArea());
        clubVo.setPresidentWx(club.getPresidentWx());

        return clubVo;
    }


    /**
     * 获得申请列表
     * @param club
     * @param userId
     * @return
     */
    private ClubMember getApply(Club club, long userId) {
        for (ClubMember apply : club.getClubInfo().getApplyList()) {
            if (apply.getUserId() == userId) {
                return apply;
            }
        }
        return null;
    }

    /**
     * 玩家是否在申请列表里
     *
     * @param club
     * @param userId
     * @return
     */
    private boolean isInApplyList(Club club, long userId) {
        for (ClubMember apply : club.getClubInfo().getApplyList()) {
            if (apply.getUserId() == userId) {
                return true;
            }
        }
        return false;
    }

    /**
     * 发送消息
     *
     * @param msgKey
     * @param msg
     */
    private void sendMsg(KafkaMsgKey msgKey, Object msg) {
        kafkaMsgProducer.send2Partition(IKafaTopic.GATE_TOPIC, msgKey.getPartition(), "" + msgKey.getUserId(), msg);
    }

    /**
     * 俱乐部加成员
     *
     * @param club
     * @param apply
     */
    private void clubAddMember(Club club, ClubMember apply) {
        ClubMember member = new ClubMember();
        member.setUserId(apply.getUserId());
        member.setTime(System.currentTimeMillis());
        member.setImage(apply.getImage());
        member.setName(apply.getName());

        club.getClubInfo().getMember().put("" + apply.getUserId(), member);

        //加到全局列表
        ClubManager.getInstance().userAddClub(apply.getUserId(), club.getId());

    }

    /**
     * 俱乐部加入成员
     * @param club
     * @param userBean
     */
    private void clubAddMember(Club club, UserBean userBean) {
        ClubMember member = new ClubMember();
        member.setUserId(userBean.getId());
        member.setTime(System.currentTimeMillis());
        member.setImage(userBean.getImage());
        member.setName(userBean.getUsername());

        club.getClubInfo().getMember().put("" + userBean.getId(), member);

        //加到全局列表
        ClubManager.getInstance().userAddClub(userBean.getId(), club.getId());
    }

    /**
     * 俱乐部删除成员
     *
     * @param club
     * @param userId
     */
    private void clubRemoveMember(Club club, long userId) {
        club.getClubInfo().getMember().remove("" + userId);

        //全局列表
        ClubManager.getInstance().userRemoveClub(userId, club.getId());
    }

    public ClubService getClubService() {
        return clubService;
    }

    public GameClubService setClubService(ClubService clubService) {
        this.clubService = clubService;
        return this;
    }
}
