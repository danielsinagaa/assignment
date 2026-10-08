package com.finshot.remittance.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.finshot.remittance.IntegrationTest;
import com.finshot.remittance.common.error.LimitExceededException;

/** Many requests released at the same instant, to prove the database-level guards work. */
class TransferConcurrencyTest extends IntegrationTest {

    private static final int THREADS = 10;

    @Autowired
    TransferService transferService;

    @Test
    void sameIdempotencyKeyAtTheSameTimeCreatesOneTransfer() throws Exception {
        List<Object> results = runConcurrently(i -> () ->
                transferService.create("same-key", request("C001", "500000")).transfer().getTransferId());

        assertThat(results).allSatisfy(result -> assertThat(result).isInstanceOf(String.class));
        assertThat(results).containsOnly(results.get(0)); // everyone got the same transferId
        assertThat(countTransfers()).isEqualTo(1);
    }

    @Test
    void dailyLimitHoldsWhenRequestsArriveTogether() throws Exception {
        // 10 x 500,000 at once. Only 6 fit in 3,000,000.
        List<Object> results = runConcurrently(i -> () ->
                transferService.create("limit-" + i, request("C002", "500000")).transfer());

        long created = results.stream().filter(Transfer.class::isInstance).count();
        long rejected = results.stream().filter(LimitExceededException.class::isInstance).count();
        assertThat(created).isEqualTo(6);
        assertThat(rejected).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT SUM(send_amount) FROM transfers WHERE customer_id = 'C002'", Long.class))
                .isEqualTo(3_000_000L);
    }

    /** Runs THREADS tasks that all start together. Returns each task's result or the exception it threw. */
    private static List<Object> runConcurrently(IntFunction<Callable<Object>> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                Callable<Object> work = task.apply(i);
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        return work.call();
                    } catch (RuntimeException e) {
                        return e;
                    }
                }));
            }
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
