package cz.mibcon.zazitky.cardprint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.jayway.jsonpath.JsonPath;
import cz.mibcon.zazitky.IntegrationTest;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Card templates and printing a batch: versions, tying a batch to a version, exports and their audit. */
@IntegrationTest
class CardPrintTest {

    static final String LAYOUT = """
            {"widthMm":90,"heightMm":50,"bleedMm":3,"qrXMm":5,"qrYMm":13,"qrSizeMm":24,"qrWhiteBox":true,
             "codeXMm":57,"codeYMm":27,"codeFontPt":16,"codeAlign":"CENTER","codeColor":"#000000",
             "serialXMm":57,"serialYMm":44,"serialFontPt":7}""";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    UUID gameId;
    String slug;

    @BeforeEach
    void castleWithAGame() {
        slug = "tisk-" + UUID.randomUUID().toString().substring(0, 8);
        UUID partner = insert("INSERT INTO partner (name) VALUES ('Tiskový hrad') RETURNING id");
        UUID location = insert("INSERT INTO location (partner_id, name) VALUES (?, 'Hrad') RETURNING id", partner);
        gameId = insert("INSERT INTO game (location_id, slug, title, intro_text, outro_text) VALUES (?, ?, 'Hra', 'I', 'O')"
                + " RETURNING id", location, slug);
    }

    @Test
    void batchesAndCodesAreNumbered() throws Exception {
        UUID batch = createBatch(4);
        assertThat(jdbc.queryForList("SELECT serial_no FROM access_code WHERE batch_id = ? ORDER BY serial_no",
                Integer.class, batch)).containsExactly(1, 2, 3, 4);
        Integer batchNo = jdbc.queryForObject("SELECT batch_no FROM access_code_batch WHERE id = ?", Integer.class, batch);
        mvc.perform(get("/api/v1/admin/batches/{id}/print", batch).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchNo").value(batchNo))
                .andExpect(jsonPath("$.template").isEmpty())
                .andExpect(jsonPath("$.latestTemplate").isEmpty());
        // A code inserted directly in SQL gets the next number of its batch
        jdbc.update("INSERT INTO access_code (batch_id, code) VALUES (?, ?)", batch, "SQL" + batchNo + "XYZ");
        assertThat(jdbc.queryForObject("SELECT max(serial_no) FROM access_code WHERE batch_id = ?", Integer.class, batch))
                .isEqualTo(5);
    }

    @Test
    void theSameGraphicIsStoredOnce() throws Exception {
        byte[] pdf = TestGraphics.pdf(96, 56);
        assertThat(upload(pdf, "predni.pdf")).isEqualTo(upload(pdf, "znovu.pdf"));
        upload(TestGraphics.png(96, 56, 300), "zadni.png");
        mvc.perform(multipart("/api/v1/admin/card-template-files")
                        .file(new MockMultipartFile("file", "text.txt", "text/plain", "ahoj".getBytes()))
                        .with(admin()).with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void versionsKeepTheGraphicsOfThePreviousOne() throws Exception {
        UUID front = upload(TestGraphics.pdf(96, 56), "predni.pdf");
        UUID back = upload(TestGraphics.png(96, 56, 300), "zadni.png");
        createTemplate(null, null).andExpect(status().isBadRequest());

        createTemplate(front, back)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.template.version").value(1))
                .andExpect(jsonPath("$.template.front.fileName").value("predni.pdf"))
                .andExpect(jsonPath("$.template.layout.qrSizeMm").value(24.0))
                .andExpect(jsonPath("$.warnings").isEmpty());
        // Only the layout changes, the graphics stay
        createTemplate(null, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.template.version").value(2))
                .andExpect(jsonPath("$.template.back.id").value(back.toString()));
        mvc.perform(get("/api/v1/admin/games/{id}/card-template/versions", gameId).with(admin()))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].version").value(2));
        mvc.perform(get("/api/v1/admin/games/{id}/card-template", gameId).with(admin()))
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void refusesAGraphicOfAnotherSizeAndWarnsAboutLowResolution() throws Exception {
        UUID trimOnly = upload(TestGraphics.pdf(90, 50), "bez-spadavky.pdf");
        UUID lowRes = upload(TestGraphics.png(96, 56, 150), "male.png");
        createTemplate(trimOnly, lowRes)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("96 × 56 mm")));
        createTemplate(upload(TestGraphics.pdf(96, 56), "predni.pdf"), lowRes)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.warnings[0]").value(containsString("DPI")));
    }

    @Test
    void previewOfTheEditor() throws Exception {
        mvc.perform(post("/api/v1/admin/games/{id}/card-template/preview", gameId).param("side", "BACK")
                        .with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"layout\":" + LAYOUT + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.image").value(startsWith("data:image/png;base64,")));
    }

    @Test
    void exportsTieTheBatchToTheLatestVersionAndAreAudited() throws Exception {
        UUID batch = createBatch(5);
        export(batch, "back.pdf", "{}").andExpect(status().isConflict());

        createTemplate(upload(TestGraphics.pdf(96, 56), "predni.pdf"), upload(TestGraphics.pdf(96, 56), "zadni.pdf"));
        byte[] back = export(batch, "back.pdf", "{\"cropMarks\":true}")
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Disposition", containsString("storyhold_" + slug + "_D")))
                .andExpect(header().string("Content-Disposition", containsString("_zadni_5ks.pdf")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(pages(back)).isEqualTo(5);

        byte[] reprint = export(batch, "back.pdf", "{\"serials\":\"4, 2\"}")
                .andExpect(header().string("Content-Disposition", containsString("_dotisk_2ks.pdf")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(pages(reprint)).isEqualTo(2);
        export(batch, "back.pdf", "{\"serials\":\"6\"}").andExpect(status().isBadRequest());
        export(batch, "front.pdf", "{}").andExpect(status().isOk());

        // A new version does not change the batch, it is printed the same way
        createTemplate(null, null);
        mvc.perform(get("/api/v1/admin/batches/{id}/print", batch).with(admin()))
                .andExpect(jsonPath("$.template.version").value(1))
                .andExpect(jsonPath("$.latestTemplate.version").value(2))
                .andExpect(jsonPath("$.printedAt").isString())
                .andExpect(jsonPath("$.exports.length()").value(3))
                .andExpect(jsonPath("$.exports[0].kind").value("FRONT"))
                .andExpect(jsonPath("$.exports[1].serials").value("2,4"))
                .andExpect(jsonPath("$.exports[1].codesCount").value(2))
                .andExpect(jsonPath("$.exports[1].username").value("admin"));
        mvc.perform(get("/api/v1/admin/batches").param("partnerId", partnerId().toString()).with(admin()))
                .andExpect(jsonPath("$[0].templateVersion").value(1))
                .andExpect(jsonPath("$[0].printedAt").isString());
    }

    @Test
    void zipHoldsBothSidesAndTheCodes() throws Exception {
        createTemplate(upload(TestGraphics.pdf(96, 56), "predni.pdf"), upload(TestGraphics.pdf(96, 56), "zadni.pdf"));
        UUID batch = createBatch(3);
        byte[] zip = export(batch, "all.zip", "{}")
                .andExpect(header().string("Content-Type", "application/zip"))
                .andReturn().getResponse().getContentAsByteArray();
        Map<String, byte[]> entries = new java.util.LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                entries.put(entry.getName(), in.readAllBytes());
            }
        }
        List<String> names = new ArrayList<>(entries.keySet());
        assertThat(names).hasSize(3);
        assertThat(names.get(0)).endsWith("_predni.pdf");
        assertThat(names.get(1)).endsWith("_zadni_3ks.pdf");
        assertThat(names.get(2)).isEqualTo("codes.csv");
        String csv = new String(entries.get("codes.csv"), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿poradi;kod;odkaz\r\n1;");
        assertThat(csv.lines()).hasSize(4);
    }

    @Test
    void aDemoCodeIsPrintedOnAsManyCardsAsNeeded() throws Exception {
        createTemplate(upload(TestGraphics.pdf(96, 56), "predni.pdf"), upload(TestGraphics.pdf(96, 56), "zadni.pdf"));
        String body = mvc.perform(post("/api/v1/admin/batches").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameId\":\"" + gameId + "\",\"kind\":\"DEMO\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID batch = UUID.fromString(JsonPath.read(body, "$.id"));
        String code = jdbc.queryForObject("SELECT code FROM access_code WHERE batch_id = ?", String.class, batch);

        mvc.perform(get("/api/v1/admin/batches/{id}/print", batch).with(admin()))
                .andExpect(jsonPath("$.multiSession").value(true))
                .andExpect(jsonPath("$.sharedCode").value(code.substring(0, 4) + "-" + code.substring(4)));
        byte[] back = export(batch, "back.pdf", "{\"copies\":5}")
                .andExpect(header().string("Content-Disposition", containsString("_zadni_5ks.pdf")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(pages(back)).isEqualTo(5);
        export(batch, "back.pdf", "{\"copies\":0}").andExpect(status().isBadRequest());
    }

    @Test
    void aHandedOverBatchKeepsItsVersion() throws Exception {
        createTemplate(upload(TestGraphics.pdf(96, 56), "predni.pdf"), upload(TestGraphics.pdf(96, 56), "zadni.pdf"));
        UUID batch = createBatch(2);
        export(batch, "front.pdf", "{}");
        String second = JsonPath.read(createTemplate(null, null).andReturn().getResponse().getContentAsString(),
                "$.template.id");

        bind(batch, second).andExpect(status().isOk()).andExpect(jsonPath("$.template.version").value(2));
        mvc.perform(post("/api/v1/admin/batches/{id}/handover", batch).with(admin()).with(csrf()));
        String first = jdbc.queryForObject("SELECT id::text FROM card_template WHERE game_id = ? AND version = 1",
                String.class, gameId);
        bind(batch, first).andExpect(status().isConflict());
    }

    @Test
    void previewAndTestPrint() throws Exception {
        createTemplate(upload(TestGraphics.pdf(96, 56), "predni.pdf"), upload(TestGraphics.pdf(96, 56), "zadni.pdf"));
        UUID batch = createBatch(1);
        mvc.perform(post("/api/v1/admin/batches/{id}/print/preview", batch).param("side", "BACK")
                        .with(admin()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.image").value(startsWith("data:image/png;base64,")));
        String template = jdbc.queryForObject("SELECT id::text FROM card_template WHERE game_id = ?", String.class, gameId);
        byte[] pdf = mvc.perform(get("/api/v1/admin/card-templates/{id}/test-print.pdf", template).with(admin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(pages(pdf)).isEqualTo(1);
        mvc.perform(get("/api/v1/admin/card-templates/{id}/files/FRONT", template).with(admin()))
                .andExpect(header().string("Content-Type", "application/pdf"));
    }

    @Test
    void languageVersionsHaveNoCards() throws Exception {
        UUID german = insert("INSERT INTO game (location_id, main_game_id, slug, title, intro_text, outro_text, language)"
                + " SELECT location_id, id, ?, 'Spiel', 'I', 'O', 'de' FROM game WHERE id = ? RETURNING id",
                slug + "-de", gameId);
        mvc.perform(get("/api/v1/admin/games/{id}/card-template/versions", german).with(admin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyForTheAdmin() throws Exception {
        UUID batch = createBatch(1);
        mvc.perform(post("/api/v1/admin/batches/{id}/print/back.pdf", batch).with(user("hrad").roles("PARTNER"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    private UUID upload(byte[] content, String name) throws Exception {
        String body = mvc.perform(multipart("/api/v1/admin/card-template-files")
                        .file(new MockMultipartFile("file", name, "application/octet-stream", content))
                        .with(admin()).with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private ResultActions createTemplate(UUID front, UUID back) throws Exception {
        return mvc.perform(post("/api/v1/admin/games/{id}/card-template", gameId).with(admin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"layout\":" + LAYOUT + ",\"frontFileId\":" + json(front) + ",\"backFileId\":" + json(back)
                        + "}"));
    }

    private ResultActions export(UUID batch, String file, String body) throws Exception {
        return mvc.perform(post("/api/v1/admin/batches/{id}/print/" + file, batch).with(admin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions bind(UUID batch, String templateId) throws Exception {
        return mvc.perform(post("/api/v1/admin/batches/{id}/print/template", batch).with(admin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"templateId\":\"" + templateId + "\"}"));
    }

    private UUID createBatch(int quantity) throws Exception {
        String body = mvc.perform(post("/api/v1/admin/batches").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameId\":\"" + gameId + "\",\"quantity\":" + quantity + ",\"validityHours\":5}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private UUID partnerId() {
        return jdbc.queryForObject("SELECT l.partner_id FROM game g JOIN location l ON l.id = g.location_id WHERE g.id = ?",
                UUID.class, gameId);
    }

    private static int pages(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return document.getNumberOfPages();
        }
    }

    private static String json(UUID id) {
        return id == null ? "null" : "\"" + id + "\"";
    }

    private UUID insert(String sql, Object... args) {
        return jdbc.queryForObject(sql, UUID.class, args);
    }

    private static RequestPostProcessor admin() {
        return user("admin").roles("ADMIN");
    }
}
