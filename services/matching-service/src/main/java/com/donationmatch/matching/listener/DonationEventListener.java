package com.donationmatch.matching.listener;

import com.donationmatch.matching.entity.Lot;
import com.donationmatch.matching.event.DonationLotCreatedEvent;
import com.donationmatch.matching.repository.LotRepository;
import com.donationmatch.matching.service.MatchingService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DonationEventListener {

    private final LotRepository lotRepository;
    private final MatchingService matchingService;

    public DonationEventListener(LotRepository lotRepository, MatchingService matchingService) {
        this.lotRepository = lotRepository;
        this.matchingService = matchingService;
    }

    @KafkaListener(topics = "donation-lot-created", containerFactory = "donationListenerFactory")
    public void handleLotCreated(DonationLotCreatedEvent event) {
        Lot lot = new Lot();
        lot.setId(event.lotId());
        lot.setItemType(event.itemType());
        lot.setQuantityAvailable(event.quantityAvailable());
        lot.setExpiryDate(event.expiryDate());
        lotRepository.save(lot);

        matchingService.matchNewLot(lot);
    }
}
