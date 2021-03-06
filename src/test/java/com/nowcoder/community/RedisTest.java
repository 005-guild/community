package com.nowcoder.community;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ContextConfiguration(classes = CommunityApplication.class)
public class RedisTest {

    @Autowired
    RedisTemplate<String,String> redisTemplate;

    @Test
    public void Test(){
        redisTemplate.opsForValue().set("a","2");
        System.out.println(redisTemplate.opsForValue().get("a"));
    }
}
