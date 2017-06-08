package com.code.server.redis.dao;

import com.code.server.constant.game.UserBean;

import java.util.List;

/**
 * Created by sunxianping on 2017/5/25.
 */
public interface IUserRedis {

    double getUserMoney(long userId);

    double addUserMoney(long userId, double money);

    void setUserMoney(long userId, double money);


    UserBean getUserBean(long userId);

    void setUserBean(UserBean userBean);

    String getAccountByUserId(long userId);

    void setUserIdAccount(long userId, String account);

    String getUserIdByAccount (long account);

    void setAccountUserId(String account, long userId);

    String getOpenIdByUserId(long userId);

    void setUserIdOpenId(long userId, String openId);

    String getUserIdByOpenId (String openId);

    void setOpenIdUserId(String openId, long userId);

    List<UserBean> getUserBeans(List<Long> users);


    void updateUserBean(long userId, UserBean userBean);




}
