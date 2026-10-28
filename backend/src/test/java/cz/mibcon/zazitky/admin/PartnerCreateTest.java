package cz.mibcon.zazitky.admin;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;

import com.jayway.jsonpath.JsonPath;
import cz.mibcon.zazitky.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@IntegrationTest
class PartnerCreateTest {

    @Autowired
    MockMvc mvc;

    @Test
    void createsPartnerWithCompanyDetails() throws Exception {
        String ico = randomIco();
        String body = create("{\"name\":\"Hrad Loket, p. o.\",\"companyId\":\"" + ico + "\","
                        + "\"vatId\":\"cz" + ico + "\",\"contactEmail\":\"pokladna@hrad-loket.cz\","
                        + "\"contactPerson\":\"Jana Nováková\",\"phone\":\"+420 352 684 648\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Hrad Loket, p. o."))
                .andExpect(jsonPath("$.companyId").value(ico))
                .andExpect(jsonPath("$.vatId").value("CZ" + ico))
                .andExpect(jsonPath("$.contactPerson").value("Jana Nováková"))
                .andExpect(jsonPath("$.phone").value("+420 352 684 648"))
                .andExpect(jsonPath("$.currentSharePct").isEmpty())
                .andExpect(jsonPath("$.shares").isEmpty())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String id = JsonPath.read(body, "$.id");

        mvc.perform(get("/api/v1/admin/partners/{id}", id).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactEmail").value("pokladna@hrad-loket.cz"));
        mvc.perform(put("/api/v1/admin/partners/{id}", id).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hrad Loket\",\"companyId\":\"" + ico + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Hrad Loket"))
                .andExpect(jsonPath("$.phone").isEmpty());
    }

    @Test
    void rejectsInvalidOrDuplicateCompanyDetails() throws Exception {
        String ico = randomIco();
        create("{\"name\":\"A\",\"companyId\":\"" + ico + "\"}").andExpect(status().isCreated());

        create("{\"name\":\"B\",\"companyId\":\"" + ico + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Partner s IČ " + ico + " už existuje."));
        create("{\"name\":\"C\",\"companyId\":\"1234\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("companyId"));
        create("{\"name\":\"D\",\"vatId\":\"123\"}").andExpect(status().isBadRequest());
        create("{\"name\":\"E\",\"phone\":\"volat večer\"}").andExpect(status().isBadRequest());
        create("{\"name\":\" \"}").andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/admin/partners").with(user("hrad").roles(PartnerUserDetails.ROLE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\"}"))
                .andExpect(status().isForbidden());
    }

    private ResultActions create(String json) throws Exception {
        return mvc.perform(post("/api/v1/admin/partners").with(admin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.getBytes(StandardCharsets.UTF_8)));
    }

    private static String randomIco() {
        return String.valueOf(ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999));
    }

    private static RequestPostProcessor admin() {
        return user("admin").roles("ADMIN");
    }
}
