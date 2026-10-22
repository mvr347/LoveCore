package dev.lovelace.lovecore.api.presence;

import java.util.Objects;

/** Подписанная текстура скина в том виде, как её отдаёт Mojang: base64-значение и подпись. */
public record SkinTexture(String value, String signature) {

    public SkinTexture {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(signature, "signature");
    }
}
