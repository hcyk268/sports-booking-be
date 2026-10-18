package dev.booking.sports.shared.upload;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import dev.booking.sports.court.domain.model.Court;
import dev.booking.sports.court.domain.repository.*;
import dev.booking.sports.identity.infrastructure.security.AuthPrincipal;
import dev.booking.sports.support.BaseIntegrationTest;
import dev.booking.sports.support.IntegrationTest;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@IntegrationTest @AutoConfigureMockMvc @Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UploadWorkflowIntegrationTest extends BaseIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired RabbitListenerEndpointRegistry listeners;
    @Autowired JdbcTemplate jdbc;
    @Autowired CourtRepository courts;
    @Autowired SportRepository sports;
    @Autowired CourtImageRepository images;
    @Autowired UploadAssetRepository assets;
    @MockitoBean ObjectStorage storage;
    private UUID owner, courtId;
    private byte[] png;
    private UsernamePasswordAuthenticationToken auth(UUID actor, String role) {
        return UsernamePasswordAuthenticationToken.authenticated(
                new AuthPrincipal(actor, "test@example.com", "test-jti", Set.of(role), Set.of()), null,
                AuthorityUtils.createAuthorityList("ROLE_" + role));
    }
    @BeforeEach void setup() throws Exception {
        // This extra MockMvc/storage-mock context must never consume another test's email queue.
        listeners.stop();
        owner = UUID.randomUUID();
        jdbc.update("insert into users(id,email,password_hash,full_name,status) values (?,?,?,?,?)",
                owner, owner + "@example.com", "unused-test-hash", "Upload Test", "ACTIVE");
        Court court = new Court(); court.setOwnerId(owner); court.setSport(sports.findAll().getFirst());
        court.setName("Upload Test Court"); court.setAddress("Test address"); court.setCity("Test city");
        courtId = courts.saveAndFlush(court).getId();
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB), "png", output);
        png = output.toByteArray();
        when(storage.presignPut(anyString(), anyString(), anyString(), any())).thenReturn(
                new ObjectStorage.PutGrant(URI.create("https://test.r2.cloudflarestorage.com/staging/test"), Map.of("content-type", "image/png")));
    }
    private String initiation() {
        return "{\"purpose\":\"COURT_IMAGE\",\"resourceId\":\"" + courtId
                + "\",\"fileName\":\"court.png\",\"contentType\":\"image/png\",\"sizeBytes\":" + png.length + "}";
    }
    private UploadAsset begin() throws Exception {
        mvc.perform(post("/api/v1/uploads").with(authentication(auth(owner,"PROVIDER")))
                .contentType(MediaType.APPLICATION_JSON).content(initiation())).andExpect(status().isCreated());
        return assets.findAll().stream().filter(value -> value.getOwnerId().equals(owner)).findFirst().orElseThrow();
    }
    @Test void realDatabaseWorkflowVerifiesAttachesAndPreservesDraftPrivacy() throws Exception {
        UploadAsset asset = begin();
        when(storage.head(asset.getStagingKey())).thenReturn(new ObjectStorage.Metadata(png.length,"image/png","etag",Map.of("upload-id",asset.getId().toString())));
        when(storage.readForValidation(asset.getStagingKey(),"etag",png.length)).thenReturn(png);
        when(storage.presignGet(anyString(),any())).thenReturn(URI.create("https://test.r2.cloudflarestorage.com/private-read"));
        String complete = "/api/v1/uploads/" + asset.getId() + "/complete";
        mvc.perform(post(complete).with(authentication(auth(owner,"PROVIDER"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("READY"));
        String gallery = "/api/v1/provider/courts/" + courtId + "/images";
        String body = "{\"uploadId\":\"" + asset.getId() + "\"}";
        mvc.perform(post(gallery).with(authentication(auth(owner,"PROVIDER"))).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.cover").value(true));
        mvc.perform(post(gallery).with(authentication(auth(owner,"PROVIDER"))).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        assertThat(images.countByCourt_Id(courtId)).isEqualTo(1);
        var image = images.findByUploadId(asset.getId()).orElseThrow();
        assertThat(assets.findById(asset.getId()).orElseThrow().getStatus()).isEqualTo(UploadAsset.Status.ATTACHED);
        mvc.perform(get("/api/v1/courts/" + courtId + "/images/" + image.getId() + "/content")).andExpect(status().isNotFound());
        mvc.perform(get(gallery + "/" + image.getId() + "/content").with(authentication(auth(owner,"PROVIDER"))))
                .andExpect(status().isFound()).andExpect(header().string("Location","https://test.r2.cloudflarestorage.com/private-read"));
        mvc.perform(delete(gallery + "/" + image.getId()).with(authentication(auth(owner,"PROVIDER")))).andExpect(status().isNoContent());
        assertThat(assets.findById(asset.getId()).orElseThrow().getStatus()).isEqualTo(UploadAsset.Status.DELETE_PENDING);
        verify(storage,never()).delete(anyString());
    }
    @Test void anonymousCustomerAndOtherProviderCannotRequestUploadForThisCourt() throws Exception {
        mvc.perform(post("/api/v1/uploads").contentType(MediaType.APPLICATION_JSON).content(initiation())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/uploads").with(authentication(auth(owner,"CUSTOMER"))).contentType(MediaType.APPLICATION_JSON).content(initiation())).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/uploads").with(authentication(auth(UUID.randomUUID(),"PROVIDER"))).contentType(MediaType.APPLICATION_JSON).content(initiation())).andExpect(status().isNotFound());
    }
    @Test void pendingUploadCannotBeAttachedAndWrongOwnerCannotComplete() throws Exception {
        UploadAsset asset = begin();
        mvc.perform(post("/api/v1/uploads/" + asset.getId() + "/complete").with(authentication(auth(UUID.randomUUID(),"PROVIDER"))))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/provider/courts/" + courtId + "/images").with(authentication(auth(owner,"PROVIDER")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"uploadId\":\"" + asset.getId() + "\"}"))
                .andExpect(status().isConflict());
        assertThat(images.countByCourt_Id(courtId)).isZero();
    }
}
