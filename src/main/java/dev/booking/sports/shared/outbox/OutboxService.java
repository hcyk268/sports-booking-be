package dev.booking.sports.shared.outbox;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OutboxService {

	private final OutboxRepository repository;
	private final ObjectMapper objectMapper;

	@Transactional
	public void enqueue(String aggregateType, UUID aggregateId, String eventType, int eventVersion, Object payload) {
		String payloadJson = serialize(payload);
		repository.save(new OutboxEvent(
				aggregateType,
				aggregateId.toString(),
				eventType,
				eventVersion,
				payloadJson));
	}

	private String serialize(Object payload) {
		try {
			return objectMapper.writeValueAsString(payload);
		}
		catch (JsonProcessingException exception) {
			throw new IllegalArgumentException("Outbox payload is not serializable", exception);
		}
	}
}
