package us.dot.its.jpo.timvalidator.validator;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.locationtech.jts.geom.Coordinate;

import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerInformation;
import us.dot.its.jpo.timvalidator.gnis.GnisFeature;
import us.dot.its.jpo.timvalidator.gnis.GnisFeatureProvider;
import us.dot.its.jpo.timvalidator.gnis.GnisLookupException;
import us.dot.its.jpo.timvalidator.pojo.ValidationIssue;
import us.dot.its.jpo.timvalidator.pojo.ValidationSeverity;

/** Checks the packetID deployment-area prefix against approved Civil GNIS features. */
final class GnisPacketIdValidator {

    private static final String CHECK_NAME = "Best Practices";
    private static final String PACKET_ID_PATH = "/value/TravelerInformation/packetID";
    private static final double MINIMUM_BOUNDING_BOX_EXPANSION_METERS = 50_000.0;
    private static final double BOUNDING_BOX_DIAGONAL_FACTOR = 0.20;
    private static final double MAXIMUM_BOUNDING_BOX_EXPANSION_METERS = 150_000.0;
    private static final double CLEARLY_INCONSISTENT_DISTANCE_METERS = 300_000.0;

    private final GnisFeatureProvider featureProvider;

    GnisPacketIdValidator(GnisFeatureProvider featureProvider) {
        this.featureProvider = Objects.requireNonNull(featureProvider, "featureProvider");
    }

    List<ValidationIssue> validate(TravelerInformation tim) {
        byte[] packetId = tim.getPacketID() == null ? null : tim.getPacketID().getOctets();
        if (packetId == null || packetId.length < 3) {
            return List.of();
        }

        // The first three bytes contain the unsigned, big-endian GNIS identifier
        int gnisId = Byte.toUnsignedInt(packetId[0]) << 16
                | Byte.toUnsignedInt(packetId[1]) << 8
                | Byte.toUnsignedInt(packetId[2]);
        try {
            // The derived identifier must first exist in the approved Civil feature set
            Optional<GnisFeature> lookup = featureProvider.findById(gnisId);
            if (lookup.isEmpty()) {
                return List.of(warning(String.format(
                        Locale.ROOT,
                        "packetID GNIS identifier %d is not an approved Civil GNIS feature",
                        gnisId)));
            }

            GnisFeature selectedFeature = lookup.orElseThrow();

            // Derive the overall geographic bounds from the TIM's path or circle regions
            Optional<TimGeographicBounds> timBounds = TimGeographicBounds.from(tim);
            if (timBounds.isEmpty()) {
                return List.of(unverifiedWarning(
                        gnisId,
                        selectedFeature,
                        "the TIM does not contain usable region geometry"));
            }

            TimGeographicBounds bounds = timBounds.orElseThrow();
            double expansionMeters = expansionMeters(bounds.diagonalMeters());
            Coordinate selectedLocation = selectedFeature.location();
            if (!isUsableLocation(selectedLocation)) {
                return List.of(unverifiedWarning(
                        gnisId,
                        selectedFeature,
                        "the GNIS feature does not contain a usable representative location"));
            }

            // Pass without a flag when the selected feature is in the expanded search area
            for (GnisBounds expandedBounds : bounds.expanded(expansionMeters)) {
                if (expandedBounds.contains(selectedLocation.getX(), selectedLocation.getY())) {
                    return List.of();
                }
            }

            // Outside the search area, distinguish uncertain from clearly inconsistent
            double distanceMeters = bounds.distanceMeters(selectedLocation);
            if (distanceMeters > CLEARLY_INCONSISTENT_DISTANCE_METERS) {
                return List.of(warning(String.format(
                        Locale.ROOT,
                        "packetID GNIS identifier %d (%s) is geographically inconsistent with the TIM bounds (feature is %.0f km from the bounds)",
                        gnisId,
                        displayName(selectedFeature),
                        distanceMeters / 1_000.0)));
            }

            // A valid Civil feature in the intermediate range cannot be verified confidently
            return List.of(unverifiedWarning(
                    gnisId,
                    selectedFeature,
                    String.format(
                            Locale.ROOT,
                            "the feature is %.0f km from the TIM bounds and outside the %.0f km expanded search area",
                            distanceMeters / 1_000.0,
                            expansionMeters / 1_000.0)));
        } catch (GnisLookupException ex) {
            return List.of(warning(String.format(
                    Locale.ROOT,
                    "packetID GNIS identifier %d could not be verified against the TIM bounds: %s",
                    gnisId,
                    ex.getMessage())));
        }
    }

    static double expansionMeters(double diagonalMeters) {
        return Math.min(
                MAXIMUM_BOUNDING_BOX_EXPANSION_METERS,
                Math.max(
                        MINIMUM_BOUNDING_BOX_EXPANSION_METERS,
                        diagonalMeters * BOUNDING_BOX_DIAGONAL_FACTOR));
    }

    private static boolean isUsableLocation(Coordinate location) {
        return location != null
                && location.isValid()
                && location.getX() >= -180.0
                && location.getX() <= 180.0
                && location.getY() >= -90.0
                && location.getY() <= 90.0;
    }

    private static ValidationIssue unverifiedWarning(
            int gnisId,
            GnisFeature feature,
            String reason) {
        return warning(String.format(
                Locale.ROOT,
                "packetID GNIS identifier %d (%s) exists, but its relationship to the TIM bounds could not be confidently verified because %s",
                gnisId,
                displayName(feature),
                reason));
    }

    private static String displayName(GnisFeature feature) {
        return feature.name() == null || feature.name().isBlank()
                ? "unnamed feature"
                : feature.name();
    }

    private static ValidationIssue warning(String message) {
        return new ValidationIssue(
                ValidationSeverity.WARNING,
                CHECK_NAME,
                message,
                PACKET_ID_PATH);
    }
}
