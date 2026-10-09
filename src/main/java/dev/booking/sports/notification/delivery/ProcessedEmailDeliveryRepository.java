package dev.booking.sports.notification.delivery;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedEmailDeliveryRepository extends JpaRepository<ProcessedEmailDelivery, UUID> {
}
