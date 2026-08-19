package com.donationmatch.donation.scheduler;

import com.donationmatch.donation.repository.LotRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
public class LotExpiryScheduler {

    private static final long POLL_INTERVAL_MS = 5 * 60 * 1000;

    private final LotRepository lotRepository;

    public LotExpiryScheduler(LotRepository lotRepository) {
        this.lotRepository = lotRepository;
    }

    @Transactional
    @Scheduled(fixedRate = POLL_INTERVAL_MS)
    public void expireStaleLots() {
        int expired = lotRepository.expireStaleLots();
        if (expired > 0) {
            log.info("Marked {} lot(s) as expired", expired);
        }
    }
}
