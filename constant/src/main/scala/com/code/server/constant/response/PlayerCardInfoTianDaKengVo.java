package com.code.server.constant.response;


import java.util.ArrayList;
import java.util.List;

/**
 * Created by sunxianping on 2017/3/20.
 */
public class PlayerCardInfoTianDaKengVo {
    public long userId;
    public List<Integer> myselfCards = new ArrayList<>();//手上的牌(暗)
    public List<Integer> everyknowCards = new ArrayList<>();//手上的牌(明)
    public List<Integer> allCards = new ArrayList<>();//手上的牌

    public PlayerCardInfoTianDaKengVo() {
    }


}
