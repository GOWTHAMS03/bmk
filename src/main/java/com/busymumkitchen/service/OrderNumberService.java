package com.busymumkitchen.service;

import com.busymumkitchen.model.DailyOrderSequence;
import com.busymumkitchen.repository.DailyOrderSequenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderNumberService {

    private final DailyOrderSequenceRepository sequenceRepository;

    @Transactional
    public String getNextDailyNumber() {
        LocalDate today = LocalDate.now();

        DailyOrderSequence seq = sequenceRepository.findByDateForUpdate(today)
                .orElse(null);

        if (seq == null) {
            seq = DailyOrderSequence.builder()
                    .orderDate(today)
                    .lastSequence(1)
                    .build();
        } else {
            seq.setLastSequence(seq.getLastSequence() + 1);
        }

        sequenceRepository.save(seq);

        String dailyNumber = String.format("#%03d", seq.getLastSequence());
        log.info("Generated daily order number: {} for date: {}", dailyNumber, today);
        return dailyNumber;
    }
}

