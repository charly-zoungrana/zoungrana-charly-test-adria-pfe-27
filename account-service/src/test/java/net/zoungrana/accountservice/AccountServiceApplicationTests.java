package net.zoungrana.accountservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@org.springframework.test.context.TestPropertySource(properties = {"spring.cloud.config.enabled=false", "spring.cloud.discovery.enabled=false", "BANK_CONFIG_IMPORT=optional:classpath:/disabled-config.properties", "bank.transaction.max-amount=1000"})
@SpringBootTest
class AccountServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
