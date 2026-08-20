package com.donationmatch.matching.service;

import com.donationmatch.matching.entity.Allocation;
import com.donationmatch.matching.entity.AllocationStatus;
import com.donationmatch.matching.entity.ItemType;
import com.donationmatch.matching.entity.Lot;
import com.donationmatch.matching.entity.Request;
import com.donationmatch.matching.repository.AllocationRepository;
import com.donationmatch.matching.repository.LotRepository;
import com.donationmatch.matching.repository.RequestRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({AllocationService.class, AllocationServiceTest.MeterRegistryTestConfig.class})
@Testcontainers
class AllocationServiceTest {

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

    @Autowired
    private AllocationRepository allocationRepository;

    private Lot saveLot(int quantity, Instant expiryDate) {
        Lot lot = new Lot();
        lot.setId(UUID.randomUUID());
        lot.setItemType(ItemType.CANNED_VEGETABLES);
        lot.setQuantityAvailable(quantity);
        lot.setExpiryDate(expiryDate);
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
    void fullyAllocatesWhenLotAndRequestQuantitiesMatch() {
        Lot lot = saveLot(10, Instant.now().plus(30, ChronoUnit.DAYS));
        Request request = saveRequest(10);

        Optional<Allocation> result = allocationService.tryAllocate(lot.getId(), request.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getQuantity()).isEqualTo(10);
        assertThat(result.get().getStatus()).isEqualTo(AllocationStatus.PENDING_PICKUP);
    }

    @Test
    void allocationIsCappedByLotQuantityWhenRequestWantsMore() {
        Lot lot = saveLot(5, Instant.now().plus(30, ChronoUnit.DAYS));
        Request request = saveRequest(10);

        Optional<Allocation> result = allocationService.tryAllocate(lot.getId(), request.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getQuantity()).isEqualTo(5);
    }

    @Test
    void allocationIsCappedByRequestQuantityWhenLotHasMore() {
        Lot lot = saveLot(10, Instant.now().plus(30, ChronoUnit.DAYS));
        Request request = saveRequest(4);

        Optional<Allocation> result = allocationService.tryAllocate(lot.getId(), request.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getQuantity()).isEqualTo(4);
    }

    @Test
    void skipsAllocationWhenLotIsAlreadyFullyReserved() {
        Lot lot = saveLot(10, Instant.now().plus(30, ChronoUnit.DAYS));
        Request firstRequest = saveRequest(10);
        Request secondRequest = saveRequest(5);

        allocationService.tryAllocate(lot.getId(), firstRequest.getId());
        Optional<Allocation> result = allocationService.tryAllocate(lot.getId(), secondRequest.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void skipsAllocationWhenRequestIsAlreadyFullyReserved() {
        Lot firstLot = saveLot(10, Instant.now().plus(30, ChronoUnit.DAYS));
        Lot secondLot = saveLot(5, Instant.now().plus(30, ChronoUnit.DAYS));
        Request request = saveRequest(10);

        allocationService.tryAllocate(firstLot.getId(), request.getId());
        Optional<Allocation> result = allocationService.tryAllocate(secondLot.getId(), request.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void skipsAllocationWhenLotDoesNotHaveEnoughShelfLifeLeft() {
        Lot lot = saveLot(10, Instant.now().plus(1, ChronoUnit.HOURS));
        Request request = saveRequest(10);

        Optional<Allocation> result = allocationService.tryAllocate(lot.getId(), request.getId());

        assertThat(result).isEmpty();
        assertThat(allocationRepository.findByLotId(lot.getId())).isEmpty();
    }

    private Allocation saveAllocation(AllocationStatus status) {
        Allocation allocation = new Allocation();
        allocation.setLotId(UUID.randomUUID());
        allocation.setRequestId(UUID.randomUUID());
        allocation.setQuantity(5);
        allocation.setStatus(status);
        allocation.setPickupDeadline(Instant.now().plus(24, ChronoUnit.HOURS));
        allocation.setCreatedAt(Instant.now());
        return allocationRepository.save(allocation);
    }

    @Test
    void confirmPickupTransitionsFromPendingPickupToConfirmed() {
        Allocation allocation = saveAllocation(AllocationStatus.PENDING_PICKUP);

        Allocation confirmed = allocationService.confirmPickup(allocation.getId());

        assertThat(confirmed.getStatus()).isEqualTo(AllocationStatus.CONFIRMED);
        assertThat(allocationRepository.findById(allocation.getId()).get().getStatus())
                .isEqualTo(AllocationStatus.CONFIRMED);
    }

    @Test
    void confirmPickupThrowsWhenAllocationNotFound() {
        UUID missingId = UUID.randomUUID();

        assertThatThrownBy(() -> allocationService.confirmPickup(missingId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void confirmPickupThrowsWhenAllocationAlreadyConfirmed() {
        Allocation allocation = saveAllocation(AllocationStatus.CONFIRMED);

        assertThatThrownBy(() -> allocationService.confirmPickup(allocation.getId()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void expireIfOverdueTransitionsFromPendingPickupToExpired() {
        Allocation allocation = saveAllocation(AllocationStatus.PENDING_PICKUP);

        boolean expired = allocationService.expireIfOverdue(allocation.getId());

        assertThat(expired).isTrue();
        assertThat(allocationRepository.findById(allocation.getId()).get().getStatus())
                .isEqualTo(AllocationStatus.EXPIRED);
    }

    @Test
    void expireIfOverdueReturnsFalseWhenAllocationNotPending() {
        Allocation allocation = saveAllocation(AllocationStatus.CONFIRMED);

        boolean expired = allocationService.expireIfOverdue(allocation.getId());

        assertThat(expired).isFalse();
        assertThat(allocationRepository.findById(allocation.getId()).get().getStatus())
                .isEqualTo(AllocationStatus.CONFIRMED);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentAllocationsAgainstSameLotDoNotOversell() throws Exception {
        Lot lot = saveLot(10, Instant.now().plus(30, ChronoUnit.DAYS));
        Request requestA = saveRequest(10);
        Request requestB = saveRequest(10);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<Optional<Allocation>> callA = () -> {
            ready.countDown();
            start.await();
            return allocationService.tryAllocate(lot.getId(), requestA.getId());
        };
        Callable<Optional<Allocation>> callB = () -> {
            ready.countDown();
            start.await();
            return allocationService.tryAllocate(lot.getId(), requestB.getId());
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<Optional<Allocation>> futureA = executor.submit(callA);
        Future<Optional<Allocation>> futureB = executor.submit(callB);

        ready.await();
        start.countDown();

        Optional<Allocation> resultA = futureA.get(10, TimeUnit.SECONDS);
        Optional<Allocation> resultB = futureB.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        int totalAllocated = resultA.map(Allocation::getQuantity).orElse(0)
                + resultB.map(Allocation::getQuantity).orElse(0);

        assertThat(totalAllocated).isEqualTo(10);
    }
}
