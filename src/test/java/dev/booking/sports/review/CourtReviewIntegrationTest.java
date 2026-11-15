package dev.booking.sports.review;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import dev.booking.sports.identity.infrastructure.security.AuthPrincipal;
import dev.booking.sports.support.BaseIntegrationTest;
import dev.booking.sports.support.IntegrationTest;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class CourtReviewIntegrationTest extends BaseIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager entities;
    private UUID customer = UUID.randomUUID(), owner = UUID.randomUUID(), admin = UUID.randomUUID();
    private UUID court = UUID.randomUUID(), booking = UUID.randomUUID();
    private String feed, create, complete;

    private RequestPostProcessor as(UUID actor, String role) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthPrincipal(actor, "test@example.com", "test", Set.of(role), Set.of()), null,
                AuthorityUtils.createAuthorityList("ROLE_" + role)));
    }

    @BeforeEach void setup() {
        for (UUID id : Set.of(customer, owner, admin)) {
            jdbc.update("insert into users(id,email,password_hash,full_name,status) values (?,?, 'test','Test','ACTIVE')",
                    id, id + "@example.com");
        }
        jdbc.update("""
                insert into courts(id,owner_id,sport_id,name,address,city,status)
                values (?,?,'d1000000-0000-0000-0000-000000000001','Review test','Street','City','ACTIVE')
                """, court, owner);
        jdbc.update("""
                insert into bookings(id,booking_code,customer_id,total_amount,status,idempotency_key,idempotency_request_hash,confirmed_at)
                values (?, ?, ?, 100, 'CONFIRMED','test',repeat('0',64),now()-interval '3 hours')
                """, booking, booking.toString().substring(0,20), customer);
        jdbc.update("""
                insert into booking_slots(id,booking_id,court_id,start_at,end_at,price_amount,status)
                values (?,?,?,now()-interval '2 hours',now()-interval '1 hour',100,'CONFIRMED')
                """, UUID.randomUUID(), booking, court);
        feed = "/api/v1/courts/" + court + "/reviews";
        create = "/api/v1/bookings/" + booking + "/review";
        complete = "/api/v1/bookings/" + booking + "/completion";
    }

    private String entry(int stars) {
        return "{\"courtId\":\"" + court + "\",\"stars\":" + stars + ",\"comment\":\"Good court\"}";
    }

    @Test void completionCreationReplyModerationAndDeletion() throws Exception {
        mvc.perform(post(create).with(as(customer,"CUSTOMER")).contentType(MediaType.APPLICATION_JSON).content(entry(4)))
                .andExpect(status().isConflict());
        mvc.perform(post(complete).with(as(owner,"PROVIDER"))).andExpect(status().isOk());
        mvc.perform(post(create).with(as(customer,"CUSTOMER")).contentType(MediaType.APPLICATION_JSON).content(entry(4)))
                .andExpect(status().isCreated());
        mvc.perform(post(create).with(as(customer,"CUSTOMER")).contentType(MediaType.APPLICATION_JSON).content(entry(5)))
                .andExpect(status().isConflict());
        UUID id = jdbc.queryForObject("select id from court_reviews where booking_id=?", UUID.class, booking);
        String review = "/api/v1/reviews/" + id;
        String moderation = "/api/v1/admin/reviews/" + id + "/visibility";
        mvc.perform(get(create).with(as(customer,"CUSTOMER"))).andExpect(status().isOk());
        mvc.perform(put(review).with(as(customer,"CUSTOMER")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0,\"stars\":5,\"comment\":\"Updated\"}")).andExpect(status().isOk());
        mvc.perform(put("/api/v1/provider/reviews/"+id+"/reply").with(as(owner,"PROVIDER")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":1,\"text\":\"Thanks\"}")).andExpect(status().isOk());
        mvc.perform(get(feed)).andExpect(status().isOk()).andExpect(jsonPath("$.data.averageStars").value(5))
                .andExpect(jsonPath("$.data.reviews.items[0].reply").value("Thanks"));
        mvc.perform(put(moderation).with(as(admin,"ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":2,\"hidden\":true,\"reason\":\"Under review\"}")).andExpect(status().isOk());
        mvc.perform(get(feed)).andExpect(jsonPath("$.data.reviews.items").isEmpty()).andExpect(jsonPath("$.data.averageStars").doesNotExist());
        mvc.perform(get("/api/v1/admin/reviews").with(as(admin,"ADMIN"))).andExpect(status().isOk());
        mvc.perform(put(moderation).with(as(admin,"ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":3,\"hidden\":false,\"reason\":\"Approved\"}")).andExpect(status().isOk());
        mvc.perform(delete(review+"?version=2").with(as(customer,"CUSTOMER"))).andExpect(status().isConflict());
        mvc.perform(delete(review+"?version=4").with(as(customer,"CUSTOMER"))).andExpect(status().isNoContent());
        mvc.perform(get(feed)).andExpect(jsonPath("$.data.reviews.items").isEmpty());
        mvc.perform(post(create).with(as(customer,"CUSTOMER")).contentType(MediaType.APPLICATION_JSON).content(entry(5)))
                .andExpect(status().isConflict());
    }

    @Test void enforcesOwnershipTimeAndRoleBoundaries() throws Exception {
        mvc.perform(post(complete).with(as(customer,"PROVIDER"))).andExpect(status().isNotFound());
        mvc.perform(post(complete).with(as(customer,"CUSTOMER"))).andExpect(status().isForbidden());
        jdbc.update("update booking_slots set end_at=now()+interval '2 hours' where booking_id=?",booking);
        entities.clear();
        mvc.perform(post(complete).with(as(owner,"PROVIDER"))).andExpect(status().isConflict());
        jdbc.update("update booking_slots set end_at=now()-interval '1 hour' where booking_id=?",booking);
        entities.clear();
        mvc.perform(post(complete).with(as(admin,"ADMIN"))).andExpect(status().isOk());
        mvc.perform(post(create).with(as(owner,"CUSTOMER")).contentType(MediaType.APPLICATION_JSON).content(entry(4)))
                .andExpect(status().isConflict());
        mvc.perform(post(create).with(as(customer,"CUSTOMER")).contentType(MediaType.APPLICATION_JSON).content(entry(6)))
                .andExpect(status().isBadRequest());
        mvc.perform(post(create).with(as(customer,"CUSTOMER")).contentType(MediaType.APPLICATION_JSON).content(entry(1)))
                .andExpect(status().isCreated());
        UUID id = jdbc.queryForObject("select id from court_reviews where booking_id=?", UUID.class, booking);
        mvc.perform(delete("/api/v1/reviews/"+id+"?version=0").with(as(owner,"CUSTOMER"))).andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/provider/reviews/"+id+"/reply").with(as(customer,"PROVIDER")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0,\"text\":\"hello\"}")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/admin/reviews").with(as(owner,"PROVIDER"))).andExpect(status().isForbidden());
    }
}
