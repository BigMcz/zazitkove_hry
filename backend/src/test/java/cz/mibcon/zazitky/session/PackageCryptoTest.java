package cz.mibcon.zazitky.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.GeneralSecurityException;

import javax.crypto.SecretKey;

import cz.mibcon.zazitky.session.SessionPackage.Sealed;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** The case shared with the frontend (test-fixtures/offline-crypto.json): what Java seals, Web Crypto opens. */
class PackageCryptoTest {

    private static final JsonNode CASE = new ObjectMapper()
            .readTree(Path.of("..", "test-fixtures", "offline-crypto.json").toFile());

    @Test
    void sealsTheSharedCaseExactly() {
        SecretKey answerKey = PackageCrypto.answerKey(text("answer"), bytes("salt"), CASE.get("iterations").asInt());
        byte[] contentKey = bytes("contentKey");

        assertThat(PackageCrypto.seal(answerKey, PackageCrypto.decode(CASE.get("key").get("iv").asString()), contentKey))
                .isEqualTo(sealed("key"));
        assertThat(PackageCrypto.seal(PackageCrypto.contentKey(contentKey),
                PackageCrypto.decode(CASE.get("content").get("iv").asString()), PackageCrypto.utf8(text("plaintext"))))
                .isEqualTo(sealed("content"));
    }

    @Test
    void wrongAnswerDoesNotOpenTheKey() {
        SecretKey wrongKey = PackageCrypto.answerKey(text("wrongAnswer"), bytes("salt"), CASE.get("iterations").asInt());

        assertThatThrownBy(() -> PackageCrypto.open(wrongKey, sealed("key")))
                .isInstanceOf(GeneralSecurityException.class);
    }

    @Test
    void opensWhatItSeals() throws GeneralSecurityException {
        SecretKey key = PackageCrypto.contentKey(PackageCrypto.randomBytes(32));
        Sealed sealed = PackageCrypto.seal(key, PackageCrypto.utf8("Žluťoučký kůň"));

        assertThat(new String(PackageCrypto.open(key, sealed), StandardCharsets.UTF_8)).isEqualTo("Žluťoučký kůň");
    }

    private static String text(String field) {
        return CASE.get(field).asString();
    }

    private static byte[] bytes(String field) {
        return PackageCrypto.decode(text(field));
    }

    private static Sealed sealed(String field) {
        return new Sealed(CASE.get(field).get("iv").asString(), CASE.get(field).get("data").asString());
    }
}
