package com.donationmatch.request.listener;

import com.donationmatch.request.entity.ItemType;
import com.donationmatch.request.entity.Request;
import com.donationmatch.request.entity.RequestStatus;
import com.donationmatch.request.event.AllocationLifecycleEvent;
import com.donationmatch.request.event.AllocationLifecycleEventType;
import com.donationmatch.request.repository.ProcessedAllocationReleaseRepository;
import com.donationmatch.request.repository.ProcessedAllocationRepository;
import com.donationmatch.request.repository.RequestRepository;
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
    private RequestRepository requestRepository;

    @Autowired
    private ProcessedAllocationRepository processedAllocationRepository;

    @Autowired
    private ProcessedAllocationReleaseRepository processedAllocationReleaseRepository;

    private Request saveRequest(int quantityRequested, int quantityFulfilled, RequestStatus status) {
        Request request = new Request();
        request.setShelterId(UUID.randomUUID());
        request.setItemType(ItemType.CANNED_VEGETABLES);
        request.setQuantityRequested(quantityRequested);
        request.setQuantityFulfilled(quantityFulfilled);
        request.setStatus(status);
        request.setCreatedAt(Instant.now());
        return requestRepository.save(request);
    }

    private AllocationLifecycleEvent createdEvent(UUID allocationId, UUID requestId, int quantity) {
        return new AllocationLifecycleEvent(allocationId, UUID.randomUUID(), requestId, quantity, AllocationLifecycleEventType.CREATED);
    }

    private AllocationLifecycleEvent expiredEvent(UUID allocationId, UUID requestId, int quantity) {
        return new AllocationLifecycleEvent(allocationId, UUID.randomUUID(), requestId, quantity, AllocationLifecycleEventType.EXPIRED);
    }

    @Test
    void createdEventPartiallyFulfillsRequest() {
        Request request = saveRequest(10, 0, RequestStatus.OPEN);

        listener.handleAllocationLifecycleEvent(createdEvent(UUID.randomUUID(), request.getId(), 4));

        Request updated = requestRepository.findById(request.getId()).get();
        assertThat(updated.getQuantityFulfilled()).isEqualTo(4);
        assertThat(updated.getStatus()).isEqualTo(RequestStatus.PARTIALLY_FULFILLED);
    }

    @Test
    void createdEventMarksRequestFulfilledWhenQuantityMeetsRequested() {
        Request request = saveRequest(10, 0, RequestStatus.OPEN);

        listener.handleAllocationLifecycleEvent(createdEvent(UUID.randomUUID(), request.getId(), 10));

        Request updated = requestRepository.findById(request.getId()).get();
        assertThat(updated.getQuantityFulfilled()).isEqualTo(10);
        assertThat(updated.getStatus()).isEqualTo(RequestStatus.FULFILLED);
    }

    @Test
    void duplicateCreatedEventIsAppliedOnlyOnce() {
        Request request = saveRequest(10, 0, RequestStatus.OPEN);
        AllocationLifecycleEvent event = createdEvent(UUID.randomUUID(), request.getId(), 4);

        listener.handleAllocationLifecycleEvent(event);
        listener.handleAllocationLifecycleEvent(event);

        assertThat(requestRepository.findById(request.getId()).get().getQuantityFulfilled()).isEqualTo(4);
    }

    @Test
    void expiredEventReleasesQuantityBackToOpen() {
        Request request = saveRequest(10, 10, RequestStatus.FULFILLED);

        listener.handleAllocationLifecycleEvent(expiredEvent(UUID.randomUUID(), request.getId(), 10));

        Request updated = requestRepository.findById(request.getId()).get();
        assertThat(updated.getQuantityFulfilled()).isEqualTo(0);
        assertThat(updated.getStatus()).isEqualTo(RequestStatus.OPEN);
    }

    @Test
    void expiredEventPartiallyReleasesQuantity() {
        Request request = saveRequest(15, 15, RequestStatus.FULFILLED);

        listener.handleAllocationLifecycleEvent(expiredEvent(UUID.randomUUID(), request.getId(), 5));

        Request updated = requestRepository.findById(request.getId()).get();
        assertThat(updated.getQuantityFulfilled()).isEqualTo(10);
        assertThat(updated.getStatus()).isEqualTo(RequestStatus.PARTIALLY_FULFILLED);
    }

    @Test
    void duplicateExpiredEventIsAppliedOnlyOnce() {
        Request request = saveRequest(10, 10, RequestStatus.FULFILLED);
        AllocationLifecycleEvent event = expiredEvent(UUID.randomUUID(), request.getId(), 10);

        listener.handleAllocationLifecycleEvent(event);
        listener.handleAllocationLifecycleEvent(event);

        assertThat(requestRepository.findById(request.getId()).get().getQuantityFulfilled()).isEqualTo(0);
    }

    @Test
    void releaseThenRematchInGuaranteedOrderLandsOnFulfilled() {
        Request request = saveRequest(15, 15, RequestStatus.FULFILLED);
        UUID oldAllocationId = UUID.randomUUID();
        UUID newAllocationId = UUID.randomUUID();

        listener.handleAllocationLifecycleEvent(expiredEvent(oldAllocationId, request.getId(), 15));
        listener.handleAllocationLifecycleEvent(createdEvent(newAllocationId, request.getId(), 15));

        Request updated = requestRepository.findById(request.getId()).get();
        assertThat(updated.getQuantityFulfilled()).isEqualTo(15);
        assertThat(updated.getStatus()).isEqualTo(RequestStatus.FULFILLED);
    }

    @Test
    void createdAndExpiredEventsForSameAllocationBothApply() {
        Request request = saveRequest(15, 0, RequestStatus.OPEN);
        UUID allocationId = UUID.randomUUID();

        listener.handleAllocationLifecycleEvent(createdEvent(allocationId, request.getId(), 15));
        listener.handleAllocationLifecycleEvent(expiredEvent(allocationId, request.getId(), 15));

        Request updated = requestRepository.findById(request.getId()).get();
        assertThat(updated.getQuantityFulfilled()).isEqualTo(0);
        assertThat(updated.getStatus()).isEqualTo(RequestStatus.OPEN);
    }
}
