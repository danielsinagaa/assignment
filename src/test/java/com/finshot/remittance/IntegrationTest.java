package com.finshot.remittance;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.finshot.remittance.transfer.dto.CreateTransferRequest;

/** Full application with its own in-memory database. Every test starts with no transfers. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:${random.uuid};DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
public abstract class IntegrationTest {

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void deleteTransfers() {
        jdbc.update("DELETE FROM partner_callback_events");
        jdbc.update("DELETE FROM transfers");
    }

    protected static CreateTransferRequest request(String customerId, String sendAmount) {
        return new CreateTransferRequest(customerId, "KRW", sendAmount, "PHP", "Juan Dela Cruz");
    }

    protected int countTransfers() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM transfers", Integer.class);
    }
}
