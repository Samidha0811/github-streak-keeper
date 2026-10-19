package com.streakkeeper;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "streak-keeper.scheduler.enabled=false"
})
class StreakKeeperApplicationTests {

    @Test
    void contextLoads() {
    }
}
