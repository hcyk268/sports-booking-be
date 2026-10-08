package dev.booking.sports.support;

import java.lang.annotation.*;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;


@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
public @interface IntegrationTest {
}
