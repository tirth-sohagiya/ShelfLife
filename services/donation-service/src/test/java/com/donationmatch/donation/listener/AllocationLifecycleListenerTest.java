package com.donationmatch.donation.listener;

import com.donationmatch.donation.entity.ItemType;
import com.donationmatch.donation.entity.Lot;
import com.donationmatch.donation.entity.LotStatus;
import com.donationmatch.donation.event.AllocationLifecycleEvent;
import com.donationmatch.donation.event.AllocationLifecycleEventType;
import com.donationmatch.donation.repository.LotRepository;
import com.donationmatch.donation.repository.ProcessedAllocationReleaseRepository;
import com.donationmatch.donation.repository.ProcessedAllocationRepository;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({AllocationLifecycleListener.class, AllocationLifecycleListenerTest.MeterRegistryTestConfig.class})
@Testcontainers
class AllocationLifecycleListenerTest {

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
    private AllocationLifecycleListener listener;

    @Autowired
    private LotRepository lotRepository;

    @Autowired
    private ProcessedAllocationRepository processedAllocationRepository;

    @Autowired
    private ProcessedAllocationReleaseRepository processedAllocationReleaseRepository;

    private Lot saveLot(int quantityAvailable, LotStatus status) {
        Lot lot = new Lot();
        lot.setDonorId(UUID.randomUUID());
        lot.setItemType(ItemType.CANNED_VEGETABLES);
        lot.setQuantityTotal(quantityAvailable);
        lot.setQuantityAvailable(quantityAvailable);
        lot.setExpiryDate(Instant.now().plus(30, ChronoUnit.DAYS));
        lot.setReceivedAt(Instant.now());
        lot.setStatus(status);
        return lotRepository.save(lot);
    }

    private AllocationLifecycleEvent createdEvent(UUID allocationId, UUID lotId, int quantity) {
        return new AllocationLifecycleEvent(allocationId, lotId, UUID.randomUUID(), quantity, AllocationLifecycleEventType.CREATED);
    }

    private AllocationLifecycleEvent expiredEvent(UUID allocationId, UUID lotId, int quantity) {
        return new AllocationLifecycleEvent(allocationId, lotId, UUID.randomUUID(), quantity, AllocationLifecycleEventType.EXPIRED);
    }

    @Test
    void createdEventDecrementsLotQuantity() {
        Lot lot = saveLot(10, LotStatus.ACTIVE);

        listener.handleAllocationLifecycleEvent(createdEvent(UUID.randomUUID(), lot.getId(), 4));

        assertThat(lotRepository.findById(lot.getId()).get().getQuantityAvailable()).isEqualTo(6);
    }

    @Test
    void createdEventDepletesLotWhenQuantityReachesZero() {
        Lot lot = saveLot(5, LotStatus.ACTIVE);

        listener.handleAllocationLifecycleEvent(createdEvent(UUID.randomUUID(), lot.getId(), 5));

        Lot updated = lotRepository.findById(lot.getId()).get();
        assertThat(updated.getQuantityAvailable()).isEqualTo(0);
        assertThat(updated.getStatus()).isEqualTo(LotStatus.DEPLETED);
    }

    @Test
    void duplicateCreatedEventIsAppliedOnlyOnce() {
        Lot lot = saveLot(10, LotStatus.ACTIVE);
        AllocationLifecycleEvent event = createdEvent(UUID.randomUUID(), lot.getId(), 4);

        listener.handleAllocationLifecycleEvent(event);
        listener.handleAllocationLifecycleEvent(event);

        assertThat(lotRepository.findById(lot.getId()).get().getQuantityAvailable()).isEqualTo(6);
    }

    @Test
    void expiredEventReleasesQuantityAndReactivatesDepletedLot() {
        Lot lot = saveLot(0, LotStatus.DEPLETED);

        listener.handleAllocationLifecycleEvent(expiredEvent(UUID.randomUUID(), lot.getId(), 5));

        Lot updated = lotRepository.findById(lot.getId()).get();
        assertThat(updated.getQuantityAvailable()).isEqualTo(5);
        assertThat(updated.getStatus()).isEqualTo(LotStatus.ACTIVE);
    }

    @Test
    void expiredEventDoesNotReactivateAnExpiredLot() {
        Lot lot = saveLot(0, LotStatus.EXPIRED);

        listener.handleAllocationLifecycleEvent(expiredEvent(UUID.randomUUID(), lot.getId(), 5));

        Lot updated = lotRepository.findById(lot.getId()).get();
        assertThat(updated.getQuantityAvailable()).isEqualTo(5);
        assertThat(updated.getStatus()).isEqualTo(LotStatus.EXPIRED);
    }

    @Test
    void duplicateExpiredEventIsAppliedOnlyOnce() {
        Lot lot = saveLot(0, LotStatus.DEPLETED);
        AllocationLifecycleEvent event = expiredEvent(UUID.randomUUID(), lot.getId(), 5);

        listener.handleAllocationLifecycleEvent(event);
        listener.handleAllocationLifecycleEvent(event);

        assertThat(lotRepository.findById(lot.getId()).get().getQuantityAvailable()).isEqualTo(5);
    }

    @Test
    void createdAndExpiredEventsForSameAllocationBothApply() {
        Lot lot = saveLot(10, LotStatus.ACTIVE);
        UUID allocationId = UUID.randomUUID();

        listener.handleAllocationLifecycleEvent(createdEvent(allocationId, lot.getId(), 4));
        listener.handleAllocationLifecycleEvent(expiredEvent(allocationId, lot.getId(), 4));

        assertThat(lotRepository.findById(lot.getId()).get().getQuantityAvailable()).isEqualTo(10);
    }

    @Test
    void releaseThenRematchInGuaranteedOrderLeavesQuantityUnchanged() {
        Lot lot = saveLot(0, LotStatus.DEPLETED);
        UUID oldAllocationId = UUID.randomUUID();
        UUID newAllocationId = UUID.randomUUID();

        listener.handleAllocationLifecycleEvent(expiredEvent(oldAllocationId, lot.getId(), 15));
        listener.handleAllocationLifecycleEvent(createdEvent(newAllocationId, lot.getId(), 15));

        Lot updated = lotRepository.findById(lot.getId()).get();
        assertThat(updated.getQuantityAvailable()).isEqualTo(0);
        assertThat(updated.getStatus()).isEqualTo(LotStatus.DEPLETED);
    }
}
