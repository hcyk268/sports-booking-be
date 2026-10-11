package dev.booking.sports.court.application;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.booking.sports.court.api.dto.request.UpdatePriceRulesRequest;
import dev.booking.sports.court.api.dto.request.UpdatePriceRulesRequest.PriceRule;
import dev.booking.sports.court.api.dto.response.PriceRuleResponse;
import dev.booking.sports.court.api.error.CourtErrorCode;
import dev.booking.sports.court.domain.model.Court;
import dev.booking.sports.court.domain.model.CourtPriceRule;
import dev.booking.sports.court.domain.repository.CourtPriceRuleRepository;
import dev.booking.sports.court.domain.repository.CourtRepository;
import dev.booking.sports.shared.exception.ApiException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CourtPriceRuleService {

    private final CourtRepository courtRepository;
    private final CourtPriceRuleRepository priceRuleRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<PriceRuleResponse> getPriceRules(
            UUID ownerId,
            UUID courtId) {

        requireOwnedCourt(ownerId, courtId);

        return toResponses(
                priceRuleRepository.findByCourt_Id(courtId));
    }

    @Transactional
    public List<PriceRuleResponse> updatePriceRules(
            UUID ownerId,
            UUID courtId,
            UpdatePriceRulesRequest request) {

        Court court = requireOwnedCourt(ownerId, courtId);

        entityManager.lock(
                court,
                LockModeType.PESSIMISTIC_WRITE);

        validateRules(request.rules());

        List<CourtPriceRule> existing =
                priceRuleRepository.findByCourt_Id(courtId);

        priceRuleRepository.deleteAll(existing);
        priceRuleRepository.flush();

        List<CourtPriceRule> replacement = new ArrayList<>();

        for (PriceRule item : request.rules()) {
            CourtPriceRule rule = new CourtPriceRule();
            rule.setCourt(court);
            rule.setDayOfWeek(item.dayOfWeek());
            rule.setStartTime(item.startTime());
            rule.setEndTime(item.endTime());
            rule.setPricePerHour(item.pricePerHour());

            replacement.add(rule);
        }

        return toResponses(
                priceRuleRepository.saveAll(replacement));
    }

    private Court requireOwnedCourt(
            UUID ownerId,
            UUID courtId) {

        return courtRepository
                .findByIdAndOwnerId(courtId, ownerId)
                .orElseThrow(() ->
                        new ApiException(
                                CourtErrorCode.COURT_NOT_FOUND));
    }

    private void validateRules(List<PriceRule> rules) {
        for (PriceRule rule : rules) {
            if (!isWholeMinute(rule.startTime())
                    || !isWholeMinute(rule.endTime())
                    || !rule.startTime().isBefore(rule.endTime())) {

                throw new ApiException(
                        CourtErrorCode.INVALID_PRICE_RULE_TIME);
            }
        }

        List<PriceRule> sorted = rules.stream()
                .sorted(
                        Comparator.comparingInt(
                                (PriceRule rule) ->
                                        rule.dayOfWeek().getValue())
                                .thenComparing(PriceRule::startTime))
                .toList();

        for (int i = 1; i < sorted.size(); i++) {
            PriceRule previous = sorted.get(i - 1);
            PriceRule current = sorted.get(i);

            if (previous.dayOfWeek() == current.dayOfWeek()
                    && current.startTime()
                            .isBefore(previous.endTime())) {

                throw new ApiException(
                        CourtErrorCode.PRICE_RULE_OVERLAP);
            }
        }
    }

    private boolean isWholeMinute(LocalTime time) {
        return time.getSecond() == 0 && time.getNano() == 0;
    }

    private List<PriceRuleResponse> toResponses(
            List<CourtPriceRule> rules) {

        return rules.stream()
                .sorted(
                        Comparator.comparingInt(
                                (CourtPriceRule rule) ->
                                        rule.getDayOfWeek().getValue())
                                .thenComparing(
                                        CourtPriceRule::getStartTime))
                .map(PriceRuleResponse::from)
                .toList();
    }
}