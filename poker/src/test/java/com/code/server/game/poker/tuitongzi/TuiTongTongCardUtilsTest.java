package com.code.server.game.poker.tuitongzi;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Created by dajuejinxian on 2018/5/16.
 */

@SpringBootTest
@RunWith(SpringRunner.class)
public class TuiTongTongCardUtilsTest {
    @Test
    public void zhuangIsBiggerThanXian() throws Exception {
    }

    @Test
    public void mAIsBiggerThanB1() throws Exception {

        // 87   // 96

        List<Integer> aList = new ArrayList<>();
        aList.add(24);
        aList.add(28);

        List<Integer> bList = new ArrayList<>();
        bList.add(32);
        bList.add(20);

        PlayerTuiTongZi playerZ = new PlayerTuiTongZi();
        PlayerTuiTongZi playerX = new PlayerTuiTongZi();

        playerX.setPlayerCards(bList);
        playerZ.setPlayerCards(aList);

        boolean  rrr =  TuiTongTongCardUtils.zhuangIsBiggerThanXian(playerZ, playerX);

        System.out.println(rrr);


    }

    @Test
    public void mAIsBiggerThanB() throws Exception {

        List<Integer> aList = new ArrayList<>();
        aList.add(0);
        aList.add(8);

        List<Integer> bList = new ArrayList<>();
        bList.add(11);
        bList.add(22);
        int ret = TuiTongTongCardUtils.mAIsBiggerThanB(aList, bList);

        System.out.println(ret);
    }

    @Test
    public void mAisBiggerTB() throws Exception {

        List<Integer> aList = new ArrayList<>();
        aList.add(0);
        aList.add(8);

        List<Integer> bList = new ArrayList<>();
        bList.add(11);
        bList.add(22);

        PlayerTuiTongZi playerTuiTongZi1 = new PlayerTuiTongZi();
        PlayerTuiTongZi playerTuiTongZi2 = new PlayerTuiTongZi();

        playerTuiTongZi1.setPlayerCards(aList);
        playerTuiTongZi2.setPlayerCards(bList);

        int ret = TuiTongTongCardUtils.mAIsBiggerThanB(playerTuiTongZi1, playerTuiTongZi2);

        System.out.println(ret);
    }

}