package com.uba.mbp.sharedplatform.auth.mfa;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class Base32CodecTest {

    @Test
    void matchesTheWellKnownRfc4648TestVectors() {
        // RFC 4648 §10 test vectors.
        assertThat(Base32Codec.encode("".getBytes(StandardCharsets.US_ASCII))).isEqualTo("");
        assertThat(Base32Codec.encode("f".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MY======".replace("=", ""));
        assertThat(Base32Codec.encode("fo".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MZXQ====".replace("=", ""));
        assertThat(Base32Codec.encode("foo".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MZXW6===".replace("=", ""));
        assertThat(Base32Codec.encode("foob".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MZXW6YQ=".replace("=", ""));
        assertThat(Base32Codec.encode("fooba".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MZXW6YTB".replace("=", ""));
        assertThat(Base32Codec.encode("foobar".getBytes(StandardCharsets.US_ASCII)))
                .isEqualTo("MZXW6YTBOI======".replace("=", ""));
    }

    @Test
    void onlyUsesValidBase32Alphabet() {
        String encoded = Base32Codec.encode("some arbitrary dev seed".getBytes(StandardCharsets.UTF_8));
        assertThat(encoded).matches("[A-Z2-7]+");
    }
}
