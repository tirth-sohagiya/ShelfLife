package com.donationmatch.matching.service;

import com.donationmatch.matching.entity.Allocation;
import com.donationmatch.matching.entity.ItemType;
import com.donationmatch.matching.entity.Lot;
import com.donationmatch.matching.entity.Request;
import com.donationmatch.matching.repository.LotRepository;
import com.donationmatch.matching.repository.RequestRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({AllocationService.class, AllocationConcurrencyBenchmarkTest.MeterRegistryTestConfig.class})
@Testcontainers
class AllocationConcurrencyBenchmarkTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @TestConfiguration
    static class MeterRegistryTestConfig {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Autowired
    private AllocationService allocationService;

    @Autowired
    private LotRepository lotRepository;

    @Autowired
    private RequestRepository requestRepository;

    private Lot saveLot(int quantity) {
        Lot lot = new Lot();
        lot.setId(UUID.randomUUID());
        lot.setItemType(ItemType.CANNED_VEGETABLES);
        lot.setQuantityAvailable(quantity);
        lot.setExpiryDate(Instant.now().plus(30, ChronoUnit.DAYS));
        return lotRepository.save(lot);
    }

    private Request saveRequest(int quantity) {
        Request request = new Request();
        request.setId(UUID.randomUUID());
        request.setShelterId(UUID.randomUUID());
        request.setItemType(ItemType.CANNED_VEGETABLES);
        request.setQuantityRequested(quantity);
        request.setCreatedAt(Instant.now());
        return requestRepository.save(request);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentAllocationsAtScaleDoNotOversellAndReportThroughput() throws Exception {
        int threadCount = 100;
        int perRequestQuantity = 10;
        int lotCapacity = threadCount * perRequestQuantity;

        Lot lot = saveLot(lotCapacity);
        List<Request> requests = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            requests.add(saveRequest(perRequestQuantity));
        }

        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Optional<Allocation>>> tasks = new ArrayList<>();
        for (Request request : requests) {
            tasks.add(() -> {
                ready.countDown();
                start.await();
                return allocationService.tryAllocate(lot.getId(), request.getId());
            });
        }

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<Optional<Allocation>>> futures = new ArrayList<>();
        for (Callable<Optional<Allocation>> task : tasks) {
            futures.add(executor.submit(task));
        }

        ready.await();
        long startNanos = System.nanoTime();
        start.countDown();

        int totalAllocated = 0;
        for (Future<Optional<Allocation>> future : futures) {
            Optional<Allocation> result = future.get(30, TimeUnit.SECONDS);
            totalAllocated += result.map(Allocation::getQuantity).orElse(0);
        }
        long elapsedNanos = System.nanoTime() - startNanos;
        executor.shutdown();

        double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
        double throughput = threadCount / elapsedSeconds;

        System.out.printf(
                "Concurrency benchmark: %d threads, %d total units allocated, %.3fs elapsed, %.1f allocations/sec%n",
                threadCount, totalAllocated, elapsedSeconds, throughput);

        assertThat(totalAllocated).isEqualTo(lotCapacity);
    }
}
