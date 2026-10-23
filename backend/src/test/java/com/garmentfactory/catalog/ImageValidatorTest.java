package com.garmentfactory.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.garmentfactory.catalog.service.ImageValidator;
import com.garmentfactory.catalog.service.ImageValidator.ValidatedImage;
import com.garmentfactory.shared.exception.ApiException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ImageValidatorTest {

    static byte[] image(int width, int height, String format) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    private static void assertRejected(byte[] bytes, String codeValue, String messagePart) {
        assertThatThrownBy(() -> ImageValidator.validate(bytes)).isInstanceOfSatisfying(ApiException.class, ex -> {
            assertThat(ex.getCode()).isEqualTo(codeValue);
            assertThat(ex.getMessage()).contains(messagePart);
        });
    }

    @Test
    void acceptsAPngAndReportsItsRealTypeAndSize() throws IOException {
        ValidatedImage result = ImageValidator.validate(image(300, 250, "png"));

        assertThat(result.contentType()).isEqualTo("image/png");
        assertThat(result.extension()).isEqualTo(".png");
        assertThat(result.width()).isEqualTo(300);
        assertThat(result.height()).isEqualTo(250);
    }

    @Test
    void acceptsAJpeg() throws IOException {
        ValidatedImage result = ImageValidator.validate(image(400, 400, "jpg"));

        assertThat(result.contentType()).isEqualTo("image/jpeg");
        assertThat(result.extension()).isEqualTo(".jpg");
    }

    @Test
    void rejectsEmptyContent() {
        assertRejected(new byte[0], "INVALID_IMAGE", "empty");
    }

    @Test
    void rejectsFilesOverFiveMegabytes() {
        assertThatThrownBy(() -> ImageValidator.validate(new byte[ImageValidator.MAX_BYTES + 1]))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
                    assertThat(ex.getCode()).isEqualTo("IMAGE_TOO_LARGE");
                });
    }

    @Test
    void rejectsValidImagesOfOtherFormats() throws IOException {
        assertRejected(image(300, 300, "gif"), "INVALID_IMAGE", "Only JPEG and PNG");
        assertRejected(image(300, 300, "bmp"), "INVALID_IMAGE", "Only JPEG and PNG");
    }

    @Test
    void rejectsTextThatMerelyClaimsToBeAnImage() {
        assertRejected("<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8), "INVALID_IMAGE", "Only JPEG and PNG");
    }

    @Test
    void rejectsAPngSignatureFollowedByGarbage() {
        byte[] fake = new byte[64];
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(signature, 0, fake, 0, signature.length);
        for (int i = signature.length; i < fake.length; i++) {
            fake[i] = (byte) (i * 31);
        }

        assertRejected(fake, "INVALID_IMAGE", "not a valid image");
    }

    @Test
    void enforcesTheDimensionLimits() throws IOException {
        assertRejected(image(100, 300, "png"), "INVALID_IMAGE", "at least");
        assertRejected(image(300, 199, "png"), "INVALID_IMAGE", "at least");
        assertRejected(image(ImageValidator.MAX_SIDE + 1, 300, "png"), "INVALID_IMAGE", "at most");
        assertThat(ImageValidator.validate(image(ImageValidator.MAX_SIDE, 300, "png")).width()).isEqualTo(4000);
    }
}