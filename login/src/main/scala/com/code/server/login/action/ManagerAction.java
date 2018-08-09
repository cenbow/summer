package com.code.server.login.action;

import com.code.server.login.config.ServerConfig;
import com.code.server.login.service.ServerManager;
import com.code.server.redis.service.RedisManager;
import com.code.server.util.SpringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.code.server.redis.config.IConstant.ROOM_USER;
import static com.code.server.redis.config.IConstant.USER_GATE;

/**
 * Created by sunxianping on 2017/6/27.
 */
@RestController
@EnableAutoConfiguration
public class ManagerAction extends Cors {

    @Autowired
    private RedisTemplate redisTemplate;

    @RequestMapping("/getOnlineUser")
    public Map<String, Object> getOnlineUser() {
        BoundHashOperations<String, String, String> user_gate = redisTemplate.boundHashOps(USER_GATE);
        BoundHashOperations<String, String, String> room = redisTemplate.boundHashOps(ROOM_USER);
        Map<String, Object> result = new HashMap<>();
        result.put("userNum", user_gate.size());
        result.put("roomNum", room.size());
        return result;
    }


    @RequestMapping("/getRoomUser")
    public Map<String, Object> getRoomUser(String roomId) {
        Map<String, Object> result = new HashMap<>();
        String serverId = RedisManager.getRoomRedisService().getServerId(roomId);
        if (serverId == null) {

            result.put("user", null);
        } else {
            result.put("user", RedisManager.getRoomRedisService().getUsers(roomId));
        }
        return result;

    }


    @RequestMapping("/getRoomUserInfo")
    public Map<String, Object> getRoomUserInfo(String roomId) {
        Map<String, Object> result = new HashMap<>();
        String serverId = RedisManager.getRoomRedisService().getServerId(roomId);
        if (serverId == null) {
            result.put("user", null);
        } else {
            Set<Long> users = RedisManager.getRoomRedisService().getUsers(roomId);
            result.put("user", RedisManager.getUserRedisService().getUserBeans(users));
        }
        return result;

    }

    @RequestMapping("/")
    public Map<String, Object> test(String roomId) {
        Map<String, Object> result = new HashMap<>();
        result.put("hello", "hello");
        return result;

    }

    @RequestMapping("/user/login")
    public AgentResponse test(String username, String password) {
        Map<String, Object> result = new HashMap<>();
        AgentResponse agentResponse = new AgentResponse();
        agentResponse.code = 20000;
        agentResponse.setData(result);

        result.put("token", "1");
        return agentResponse;

    }

    @RequestMapping("/getDownloadUrl")
    public String getDownloadUrl(String platform, String versionNow) {
        Map<String, Object> result = new HashMap<>();
        result.put("hello", "hello");
        ServerConfig serverConfig = SpringUtil.getBean(ServerConfig.class);
        ServerManager.constant.getVersionOfAndroid();
        String url = "http://" + serverConfig.getDomain() + "/client/" + platform;
        return url;
    }
}
