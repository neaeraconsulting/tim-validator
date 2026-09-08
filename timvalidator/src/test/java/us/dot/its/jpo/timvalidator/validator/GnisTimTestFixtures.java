package us.dot.its.jpo.timvalidator.validator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerInformation;
import us.dot.its.jpo.timvalidator.converter.JerToMessageFrameConverter;

/** Loads realistic JER TIM payloads used by GNIS geographic validation tests. */
final class GnisTimTestFixtures {

    private static final String RESOURCE_DIRECTORY = "/fixtures/gnis/";
    private static final JerToMessageFrameConverter CONVERTER =
            new JerToMessageFrameConverter();

    private GnisTimTestFixtures() {
    }

    /** Loads a named GNIS fixture and returns its typed TravelerInformation payload. */
    static TravelerInformation travelerInformation(String fileName) {
        String resourcePath = RESOURCE_DIRECTORY + fileName;
        try (InputStream input = GnisTimTestFixtures.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IllegalStateException("Missing GNIS test fixture: " + resourcePath);
            }
            var messageFrame = CONVERTER.deserialize(
                    new String(input.readAllBytes(), StandardCharsets.UTF_8));
            if (messageFrame.getValue() instanceof TravelerInformation tim) {
                return tim;
            }
            throw new IllegalStateException(
                    "GNIS fixture does not contain TravelerInformation: " + resourcePath);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load GNIS test fixture: " + resourcePath, ex);
        }
    }
}
