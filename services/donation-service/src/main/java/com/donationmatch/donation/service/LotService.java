package com.donationmatch.donation.service;

import com.donationmatch.donation.dto.CreateLotRequest;
import com.donationmatch.donation.entity.Lot;
import com.donationmatch.donation.entity.LotStatus;
import com.donationmatch.donation.event.DonationEventPublisher;
import com.donationmatch.donation.repository.LotRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class LotService {

    private final LotRepository lotRepository;
    private final DonationEventPublisher publisher;

    public LotService(LotRepository lotRepository, DonationEventPublisher publisher) {
        this.lotRepository = lotRepository;
        this.publisher = publisher;
    }

    public Lot createLot(CreateLotRequest request) {
        Lot lot = new Lot();
        lot.setDonorId(request.donorId());
        lot.setItemType(request.itemType());
        lot.setQuantityTotal(request.quantityTotal());
        lot.setQuantityAvailable(request.quantityTotal()); // fully available on creation
        lot.setExpiryDate(request.expiryDate());
        lot.setReceivedAt(Instant.now());
        lot.setStatus(LotStatus.ACTIVE);

        Lot saved = lotRepository.save(lot);
        log.info("Created lot {} - donor {}, {} units of {}, expires {}",
                saved.getId(), saved.getDonorId(), saved.getQuantityTotal(), saved.getItemType(), saved.getExpiryDate());

        publisher.publishLotCreated(saved);
        return saved;
    }

    public List<Lot> getAllLots() {
        return lotRepository.findAll();
    }

    public Lot getLotById(UUID id) {
        return lotRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Lot not found: " + id));
    }
}
