package dev.booking.sports.court.application;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.booking.sports.court.api.dto.response.SportResponse;
import dev.booking.sports.court.domain.repository.SportRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SportService {

    private final SportRepository sportRepository;

    @Transactional(readOnly = true)
    public List<SportResponse> getSports() {
        return sportRepository.findAll(Sort.by("code"))
                .stream()
                .map(sport -> new SportResponse(
                        sport.getId(),
                        sport.getCode(),
                        sport.getName(),
                        sport.getDescription()))
                .toList();
    }
}