package com.finshot.remittance.callback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.finshot.remittance.IntegrationTest;
import com.finshot.remittance.partner.PartnerClient;
import com.finshot.remittance.partner.PartnerOutcome;
import com.finshot.remittance.transfer.Transfer;
import com.finshot.remittance.transfer.TransferService;
import com.finshot.remittance.transfer.TransferStatus;

/** Part 2: send -> partner -> callback, with a scripted partner. */
class PartnerFlowTest extends IntegrationTest {

    @MockitoBean
    PartnerClient partnerClient;

    @Autowired
    TransferService transferService;

    @Autowired
    PartnerCallbackService callbackService;

    @Test
    void sameCallbackTwiceChangesStateOnce() throws Exception {
        when(partnerClient.submit(anyString())).thenReturn(PartnerOutcome.ACCEPTED);
        Transfer sending = transferService.send(newTransferId("k1"));
        assertThat(sending.getStatus()).isEqualTo(TransferStatus.SENDING); // accepted is not completed

        var callback = new PartnerCallbackRequest(sending.getPartnerRef(), "evt_0001", "COMPLETED");
        // Two deliveries at the same time: both must succeed, only one may apply.
        var first = CompletableFuture.supplyAsync(() -> callbackService.handle(callback));
        var second = CompletableFuture.supplyAsync(() -> callbackService.handle(callback));

        assertThat(first.get().getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(second.get().getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM partner_callback_events", Integer.class)).isEqualTo(1);
    }

    @Test
    void allAttemptsFailingMarksTransferFailed() {
        when(partnerClient.submit(anyString())).thenReturn(PartnerOutcome.REJECTED);

        Transfer result = transferService.send(newTransferId("k2"));

        assertThat(result.getStatus()).isEqualTo(TransferStatus.FAILED);
        assertThat(result.getFailureReason()).isNotBlank();
        verify(partnerClient, times(3)).submit(result.getPartnerRef()); // same partnerRef on every attempt
    }

    /** DESIGN.md Q5: the callback arrives while we are still busy with the partner call. */
    @Test
    void callbackBeforePartnerResultIsSavedIsNotOverwritten() {
        when(partnerClient.submit(anyString())).thenAnswer(invocation -> {
            String partnerRef = invocation.getArgument(0);
            callbackService.handle(new PartnerCallbackRequest(partnerRef, "evt-early-" + partnerRef, "COMPLETED"));
            throw new IllegalStateException("partner timed out"); // and our side thinks the call failed
        });

        Transfer result = transferService.send(newTransferId("k3"));

        assertThat(result.getStatus()).isEqualTo(TransferStatus.COMPLETED);
    }

    private String newTransferId(String key) {
        return transferService.create(key, request("C003", "100000")).transfer().getTransferId();
    }
}
