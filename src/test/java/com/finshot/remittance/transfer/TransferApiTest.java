package com.finshot.remittance.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import com.finshot.remittance.IntegrationTest;

/** S1-S3 over HTTP: exact field values, the retry answer, and 409 for a reused key. */
@AutoConfigureMockMvc
class TransferApiTest extends IntegrationTest {

    private static final String BODY = """
            {"customerId":"C001","sendCurrency":"KRW","sendAmount":"%s","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}""";

    @Autowired
    MockMvc mvc;

    @Test
    void retryReturnsTheOriginalTransferAndReusedKeyWithOtherDataIsConflict() throws Exception {
        String transferId = mvc.perform(create("key-1", "500000"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.sendAmount").value("500000"))
                .andExpect(jsonPath("$.fee").value("5000"))
                .andExpect(jsonPath("$.totalDebit").value("505000"))
                .andExpect(jsonPath("$.receiveAmount").value("20600.00"))
                .andReturn().getResponse().getHeader("Location").replace("/api/transfers/", "");

        mvc.perform(create("key-1", "500000"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.transferId").value(transferId));

        mvc.perform(create("key-1", "600000"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));

        assertThat(countTransfers()).isEqualTo(1);
    }

    @Test
    void missingIdempotencyKeyIsRejected() throws Exception {
        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(BODY.formatted("500000")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    private static RequestBuilder create(String key, String amount) {
        return post("/api/transfers")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY.formatted(amount));
    }
}
