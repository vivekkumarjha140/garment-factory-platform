package com.garmentfactory.catalog.service;

import com.garmentfactory.shared.exception.ApiException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.http.HttpStatus;

/**
 * Checks an uploaded image by what the bytes are, not by what the file claims to be: the file name
 * and the browser's content type are ignored. A file is accepted only if its first bytes are a JPEG or
 * PNG signature, an image reader can parse its header, and the dimensions are within limits. The pixels
 * are never decoded here, so an image bomb cannot exhaust memory.
 */
public final class ImageValidator {

    public record ValidatedImage(String contentType, String extension, int width, int height) {
    }

    public static final int MAX_BYTES = 5 * 1024 * 1024;
    public static final int MIN_SIDE = 200;
    public static final int MAX_SIDE = 4000;

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    static {
        // Read from memory, never from a temporary file.
        ImageIO.setUseCache(false);
    }

    private ImageValidator() {
    }

    public static ValidatedImage validate(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw invalid("The file is empty.");
        }
        if (bytes.length > MAX_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "IMAGE_TOO_LARGE", "Images can be at most 5 MB.");
        }

        String contentType;
        String extension;
        String expectedFormat;
        if (isJpeg(bytes)) {
            contentType = "image/jpeg";
            extension = ".jpg";
            expectedFormat = "jpeg";
        } else if (isPng(bytes)) {
            contentType = "image/png";
            extension = ".png";
            expectedFormat = "png";
        } else {
            throw invalid("Only JPEG and PNG images are accepted.");
        }

        int[] size = readSize(bytes, expectedFormat);
        int width = size[0];
        int height = size[1];
        if (width < MIN_SIDE || height < MIN_SIDE) {
            throw invalid("The image must be at least " + MIN_SIDE + " x " + MIN_SIDE + " pixels.");
        }
        if (width > MAX_SIDE || height > MAX_SIDE) {
            throw invalid("The image must be at most " + MAX_SIDE + " x " + MAX_SIDE + " pixels.");
        }
        return new ValidatedImage(contentType, extension, width, height);
    }

    private static boolean isJpeg(byte[] bytes) {
        return bytes.length > 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF;
    }

    private static boolean isPng(byte[] bytes) {
        if (bytes.length < PNG_SIGNATURE.length) {
            return false;
        }
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (bytes[i] != PNG_SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }

    /** Parses only the image header; the reader must agree with the signature we found. */
    private static int[] readSize(byte[] bytes, String expectedFormat) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                throw invalid("The file is not a valid image.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw invalid("The file is not a valid image.");
            }
            ImageReader reader = readers.next();
            try {
                if (!reader.getFormatName().toLowerCase(Locale.ROOT).startsWith(expectedFormat)) {
                    throw invalid("The file content does not match its image type.");
                }
                reader.setInput(input);
                return new int[]{reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof ApiException apiException) {
                throw apiException;
            }
            throw invalid("The file is not a valid image.");
        }
    }

    private static ApiException invalid(String message) {
        return ApiException.badRequest("INVALID_IMAGE", message);
    }
}