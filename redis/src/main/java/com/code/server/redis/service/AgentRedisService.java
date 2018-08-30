package com.code.server.redis.service;

import com.code.server.constant.db.ChildCost;
import com.code.server.constant.db.PartnerRebate;
import com.code.server.constant.game.AgentBean;
import com.code.server.redis.config.IConstant;
import com.code.server.redis.dao.IAgentRedis;
import com.code.server.util.JsonUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.*;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Created by sunxianping on 2018/3/13.
 */
@Service
public class AgentRedisService implements IAgentRedis, IConstant {

    private static final Map<Integer, Integer> moneyScala = new HashMap<>();
    private static final Map<Integer, Integer> goldScala = new HashMap<>();

    static {
        moneyScala.put(1, 54);
        moneyScala.put(2, 10);
        moneyScala.put(3, 10);

        goldScala.put(1, 20);
        goldScala.put(2, 10);
        goldScala.put(3, 10);
    }

    @Autowired
    private RedisTemplate redisTemplate;


    @Override
    public double addRebate(long agentId, double rebate, String date,String deleteDay) {
        HashOperations<String, String, Double> agent_rebate = redisTemplate.opsForHash();
        // 把修改后的值放入userBean里
        double m = agent_rebate.increment(AGENT_REBATE, "" + agentId, rebate);
        AgentBean agentBean = getAgentBean(agentId);
        if (agentBean != null) {
            agentBean.setRebate(m);
            if (rebate > 0) {
                //历史总返利
                double allRebate = agentBean.getAgentInfo().getAllRebate();
                allRebate += rebate;
                agentBean.getAgentInfo().setAllRebate(allRebate);
                //每日返利
                Map<String,Double> everyDayRebate = agentBean.getAgentInfo().getEveryDayRebate();

                double todayRebate = everyDayRebate.getOrDefault(date, 0D);
                todayRebate += rebate;
                everyDayRebate.putIfAbsent(date, todayRebate);
                //删除记录
                everyDayRebate.remove(deleteDay);
            }

            updateAgentBean(agentBean);
        }
        return m;
    }


    public void addChildCost(long agentId, double cost, int level,String date, String deleteDay) {
        AgentBean agentBean = getAgentBean(agentId);
        if (agentBean != null) {
            if (cost > 0) {
                if (agentBean.getAgentInfo().getEveryDayCost() == null) {
                    agentBean.getAgentInfo().setEveryDayCost(new HashMap<>());
                }
                ChildCost childCost = agentBean.getAgentInfo().getEveryDayCost().getOrDefault(date, new ChildCost());

                switch (level) {
                    case 0:
                        childCost.partner += cost;
                        break;
                    case 1:
                        childCost.firstLevel += cost;
                        break;
                    case 2:
                        childCost.secondLevel += cost;
                        break;
                    case 3:
                        childCost.thirdLevel += cost;
                        break;
                }
                agentBean.getAgentInfo().getEveryDayCost().put(date, childCost);
                agentBean.getAgentInfo().getEveryDayRebate().remove(deleteDay);
//
//                //历史总返利
//                double allRebate = agentBean.getAgentInfo().getAllRebate();
//                allRebate += rebate;
//                agentBean.getAgentInfo().setAllRebate(allRebate);
//                //每日返利
//                Map<String,Double> everyDayRebate = agentBean.getAgentInfo().getEveryDayRebate();
//
//                double todayRebate = everyDayRebate.getOrDefault(date, 0D);
//                todayRebate += rebate;
//                everyDayRebate.putIfAbsent(date, todayRebate);
//                //删除记录
//                everyDayRebate.remove(deleteDay);
                updateAgentBean(agentBean);
            }

        }
    }

    public void addPartnerRebate(long agentId, double cost, double moneyRebate, double goldRebate, String date, String deleteDay) {
        AgentBean agentBean = getAgentBean(agentId);
        if (agentBean != null) {
            if (agentBean.getAgentInfo().getEveryPartnerRebate() == null) {
                agentBean.getAgentInfo().setEveryPartnerRebate(new HashMap<>());
            }
            PartnerRebate partnerRebate = agentBean.getAgentInfo().getEveryPartnerRebate().getOrDefault(date, new PartnerRebate());
            partnerRebate.cost += cost;
            partnerRebate.moneyRebate += moneyRebate;
            partnerRebate.goldRebate += goldRebate;
            partnerRebate.allRebate += moneyRebate;
            partnerRebate.allRebate += goldRebate;

            agentBean.getAgentInfo().getEveryPartnerRebate().put(date, partnerRebate);
            agentBean.getAgentInfo().getEveryPartnerRebate().remove(deleteDay);

            updateAgentBean(agentBean);
        }
    }

    @Override
    public AgentBean getAgentBean(long agentId) {

        BoundHashOperations<String, String, String> agent_bean = redisTemplate.boundHashOps(AGENT_BEAN);
        String json = agent_bean.get(String.valueOf(agentId));
        if (json != null) {
            return JsonUtil.readValue(json, AgentBean.class);
        }
        return null;
    }

    @Override
    public void setAgentBean(AgentBean agentBean) {
        BoundHashOperations<String, String, String> agent_bean = redisTemplate.boundHashOps(AGENT_BEAN);
        agent_bean.put(String.valueOf(agentBean.getId()), JsonUtil.toJson(agentBean));
    }

    @Override
    public void updateAgentBean(AgentBean agentBean) {

        BoundHashOperations<String, String, String> agent_bean = redisTemplate.boundHashOps(AGENT_BEAN);
        agent_bean.put(String.valueOf(agentBean.getId()), JsonUtil.toJson(agentBean));
        //加入保存列表
        addSaveAgent(agentBean.getId());
    }

    @Override
    public boolean isExit(long agentId) {
        BoundHashOperations<String, String, String> agent_bean = redisTemplate.boundHashOps(AGENT_BEAN);
        return agent_bean.hasKey("" + agentId);
    }

    @Override
    public void setAgentToken(String token, Map<String, String> data, long timeout) {

        BoundValueOperations<String, String> agentData = redisTemplate.boundValueOps(getAgentToken(token));

        agentData.set(JsonUtil.toJson(data), timeout, TimeUnit.SECONDS);
    }

    @Override
    public Map<String, String> getAgentByToken(String token) {
        BoundValueOperations<String, String> agentData = redisTemplate.boundValueOps(getAgentToken(token));
        String json = agentData.get();
        if (json != null) {
            return JsonUtil.readValue(json, Map.class);
        }
        return null;
    }

    private String getAgentToken(String token) {
        return AGENT_TOKEN + token;
    }

    public void addSaveAgent(long agentId) {
        BoundSetOperations<String, String> save_agent = redisTemplate.boundSetOps(SAVE_AGENT);
        save_agent.add("" + agentId);

    }


    public void removeSaveAgent(Object... agentId) {
        BoundSetOperations<String, String> save_users = redisTemplate.boundSetOps(SAVE_AGENT);
        save_users.remove(agentId);
    }


    public Set<String> getSaveAgents() {
        return redisTemplate.boundSetOps(SAVE_AGENT).members();
    }

    private String getAgentBeanKey(long agentId) {
        return String.valueOf(agentId);
    }

    public void setAgent2Redis(AgentBean agentBean) {
        //bean
        BoundHashOperations<String, String, String> agent_bean = redisTemplate.boundHashOps(AGENT_BEAN);
        agent_bean.put(String.valueOf(agentBean.getId()), JsonUtil.toJson(agentBean));

        //rebate
        BoundHashOperations<String, String, String> agent_rebate = redisTemplate.boundHashOps(AGENT_REBATE);
        agent_rebate.put("" + agentBean.getId(), "" + agentBean.getRebate());

    }

    public void removeAgentBean(String agentId) {
        //bean
        BoundHashOperations<String,String, String> agent_bean = redisTemplate.boundHashOps(AGENT_BEAN);
        agent_bean.delete(agentId);


        //rebate
        BoundHashOperations<String,String, String> agent_rebate = redisTemplate.boundHashOps(AGENT_REBATE);
        agent_rebate.delete(agentId);

        //save list
        removeSaveAgent(agentId);

    }

    public long getAgentNum() {
        BoundHashOperations<String, String, Double> agent_rebate = redisTemplate.boundHashOps(AGENT_REBATE);
        return agent_rebate.size();
    }


//          1、直接玩家（房卡55%、金币20%）
//          2、2级代理（房卡10%、金币10%）
//          3、3级代理（房卡5%、金币10%）

    public void addRebate(long userId, long parentId, int type, double num, double childCost) {
        long agentId1 = 0;
        long agentId2 = 0;
        long agentId3 = 0;
        long partnerId = 0;

        //没有上级代理
//        if (parentId == 0) return;
        //自己是否是代理
        AgentBean agent1 = RedisManager.getAgentRedisService().getAgentBean(userId);
        if (agent1 == null) {//自己是代理则自己就是1级代理
            agent1 = RedisManager.getAgentRedisService().getAgentBean(parentId);
        }
        if (agent1 != null) {

            agentId1 = agent1.getId();
            AgentBean agent2 = RedisManager.getAgentRedisService().getAgentBean(agent1.getParentId());

            if (agent2 != null) {

                agentId2 = agent2.getId();

                agentId3 = agent2.getParentId();
            }
            //合伙人
            partnerId = agent1.getPartnerId();
        }

//        if(agent1 == null) return;

        int scala1 = getScala(type, 1);
        int scala2 = getScala(type, 2);
        int scala3 = getScala(type, 3);


        String today = LocalDate.now().toString();
        String deleteDay = LocalDate.now().minusDays(7).toString();
        String deleteDay1 = LocalDate.now().minusDays(90).toString();
        String deleteDay2 = LocalDate.now().minusDays(3).toString();
        double allRebate = 0;
        if (agentId1 != 0) {
            double n = scala1 * num / 100;
            allRebate += n;
            addRebate(agentId1, n,today,deleteDay);
            addChildCost(agentId1,childCost,1,today, deleteDay1);
        }
        if (agentId2 != 0) {
            double n = scala2 * num / 100;
            allRebate += n;
            addRebate(agentId2, n,today,deleteDay);
            addChildCost(agentId2,childCost,2,today, deleteDay1);
        }
        if (agentId3 != 0) {
            double n = scala3 * num / 100;
            allRebate += n;
            addRebate(agentId3, n,today,deleteDay);
            addChildCost(agentId3,childCost,3,today, deleteDay1);
        }

        //合伙人 10%
        if (partnerId != 0) {
            double n = 10 * num / 100;
            allRebate += n;
//            addRebate(partnerId, n,today,deleteDay);
//            addChildCost(agentId1,childCost,0,today, deleteDay1);

            System.out.println("合伙人 返利");
            if (type == 0) {
                addPartnerRebate(partnerId, childCost, n, 0, today, deleteDay2);
            }else{
                addPartnerRebate(partnerId, childCost, 0, n, today, deleteDay2);
            }
        }

        if (type == 0) {
            //房卡返利
            RedisManager.getLogRedisService().addChargeRebate(allRebate);
        }

    }

    private int getScala(int type, int level) {

        if (type == 0) {
            return moneyScala.get(level);
        } else {
            return goldScala.get(level);
        }

    }
}
