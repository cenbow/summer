package com.code.server.game.mahjong.logic;

/**
 * Created by win7 on 2016/12/26.
 */
public class RoomFactory {
    public static final int roomTypeJL = 1;//晋龙
//    大运 DY
//    晋龙 JL
//    胡同 HT
//    易和 YH

    public static RoomInfo getRoomInstance(String gameType) {
        switch (gameType) {
            case "JL"://晋龙
            case "DS"://都市
                return (RoomInfo)new RoomInfoJL().setGameType(gameType);
            case "HT":
                return ((RoomInfo)new RoomInfo().setGameType(gameType)).setHasGangBlackList(false);
            case "LQ":
                return ((RoomInfo)new RoomInfo().setGameType(gameType)).setChangeBankerAfterHuangZhuang(true);
            case "DH":
                return ((RoomInfo)new RoomInfo().setGameType(gameType)).setHasGangBlackList(false);
            case "TJ":
                return ((RoomInfo)new RoomInfo().setGameType(gameType)).setHasGangBlackList(false);
            case "NZZ":
                return ((RoomInfo)new RoomInfo().setGameType(gameType)).setHasGangBlackList(false);
            case "HM":
                return ((RoomInfo)new RoomInfo().setGameType(gameType)).setHasGangBlackList(false);
            case "NIUYEZI":
                return ((RoomInfo)new RoomInfo().setGameType(gameType)).setHasGangBlackList(false);
            case "HS":
                return (RoomInfo)new RoomInfoGoldH().setGameType(gameType);
            case "HELEGOLD":
                return (RoomInfo)new RoomInfoGoldHeLe().setGameType(gameType);
            default:
                return (RoomInfo)new RoomInfo().setGameType(gameType);
        }
    }
}
