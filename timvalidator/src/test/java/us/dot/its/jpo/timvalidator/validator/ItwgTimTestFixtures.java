package us.dot.its.jpo.timvalidator.validator;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Supplies schema-valid ITWG TIMs and JSON-tree mutation helpers for validator
 * tests. Keeping JSON construction here lets individual tests describe only
 * the rule they are exercising.
 */
final class ItwgTimTestFixtures {

    static final String TRAVELER_INFORMATION_PATH = "/value/TravelerInformation";
    static final String FRAME_PATH = TRAVELER_INFORMATION_PATH + "/dataFrames/0";
    static final String REGION_PATH = FRAME_PATH + "/regions/0";
    static final String DESCRIPTION_PATH = REGION_PATH + "/description";
    static final String CIRCLE_PATH = DESCRIPTION_PATH + "/geometry/circle";
    static final String XY_PATH = DESCRIPTION_PATH + "/path/offset/xy";
    static final String XY_NODES_PATH = XY_PATH + "/nodes";
    static final String XY_FIRST_NODE_PATH = XY_NODES_PATH + "/0";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ObjectNode CIRCLE_TEMPLATE = loadFixture("valid-circle-tim.json");
    private static final ObjectNode CLOSED_PATH_TEMPLATE = loadFixture("valid-closed-path-tim.json");

    /** Prevents instantiation of this test-only utility class. */
    private ItwgTimTestFixtures() {
    }

    /** Returns a fresh copy of a valid circle TIM. */
    static ObjectNode circleTim() {
        return CIRCLE_TEMPLATE.deepCopy();
    }

    /** Returns a fresh copy of a valid closed-path TIM. */
    static ObjectNode closedPathTim() {
        return CLOSED_PATH_TEMPLATE.deepCopy();
    }

    /** Returns a valid path TIM for the requested open/closed and XY/LL encoding. */
    static ObjectNode pathTim(NodeEncoding encoding) {
        ObjectNode root = closedPathTim();
        ObjectNode region = objectAt(root, REGION_PATH);

        if (!encoding.closedPath()) {
            region.put("laneWidth", 1);
            region.put("directionality", "forward");
            region.put("closedPath", false);
            region.remove("direction");
        }

        if (encoding.offsetName().equals("ll")) {
            convertXyNodesToLl(root);
        }
        return root;
    }

    /** Returns a valid path TIM with optional attributes on its first node. */
    static String pathTimWithNodeAttributes(NodeEncoding encoding, String attributesJson) {
        ObjectNode root = pathTim(encoding);
        if (attributesJson != null) {
            objectAt(root, encoding.firstNodePath())
                .set("attributes", parseJson(attributesJson));
        }
        return toJson(root);
    }

    /** Returns a valid circle TIM serialized as JSON. */
    static String circleTimJson() {
        return toJson(circleTim());
    }

    /** Returns a valid closed-path TIM serialized as JSON. */
    static String closedPathTimJson() {
        return toJson(closedPathTim());
    }

    /** Returns a valid open XY path TIM serialized as JSON. */
    static String openPathTimJson() {
        return toJson(pathTim(NodeEncoding.OPEN_XY));
    }

    /** Returns a circle TIM containing a valid optional contentNew value. */
    static String circleTimWithContentNewJson() {
        ObjectNode root = circleTim();
        objectAt(root, FRAME_PATH).set("contentNew", parseJson("""
            {
              "frictionInfo": {
                "roadSurfaceDescription": {
                  "asphaltOrTar": {
                    "type": "traveled"
                  }
                }
              }
            }
            """));
        return toJson(root);
    }

    /** Returns a closed-path TIM without its required direction field. */
    static String closedPathTimWithoutDirectionJson() {
        return remove(closedPathTimJson(), REGION_PATH + "/direction");
    }

    /** Returns a closed path containing too few polygon nodes. */
    static String threeNodeClosedPathTimJson() {
        return resizeArray(closedPathTimJson(), XY_NODES_PATH, 3);
    }

    /** Returns a valid TIM whose XY path is represented by a computed lane. */
    static String computedLaneTimJson() {
        ObjectNode root = closedPathTim();
        ObjectNode xy = objectAt(root, XY_PATH);
        xy.remove("nodes");
        xy.set("computed", parseJson("""
            {
              "referenceLaneId": 1,
              "offsetXaxis": {"small": 0},
              "offsetYaxis": {"small": 0}
            }
            """));
        return toJson(root);
    }

    /** Returns a TIM whose region description incorrectly contains two choices. */
    static String descriptionChoiceConflictTimJson() {
        ObjectNode root = circleTim();
        JsonNode path = closedPathTim().at(DESCRIPTION_PATH + "/path");
        objectAt(root, DESCRIPTION_PATH).set("path", path.deepCopy());
        return toJson(root);
    }

    /** Sets a field to the supplied JSON value and returns the mutated document. */
    static String set(String json, String fieldPath, String valueJson) {
        return mutate(json, root -> set(root, fieldPath, parseJson(valueJson)));
    }

    /** Removes a field and returns the mutated document. */
    static String remove(String json, String fieldPath) {
        return mutate(json, root -> parentObject(root, fieldPath).remove(fieldName(fieldPath)));
    }

    /** Resizes an existing nonempty array by removing entries or cloning its first entry. */
    static String resizeArray(String json, String arrayPath, int size) {
        return mutate(json, root -> {
            ArrayNode array = arrayAt(root, arrayPath);
            JsonNode sample = array.isEmpty() ? null : array.get(0).deepCopy();
            while (array.size() > size) {
                array.remove(array.size() - 1);
            }
            while (array.size() < size) {
                if (sample == null) {
                    throw new IllegalArgumentException("Cannot grow an empty fixture array: " + arrayPath);
                }
                array.add(sample.deepCopy());
            }
        });
    }

    /** Applies a JSON-tree mutation to a fresh parse of the supplied document. */
    static String mutate(String json, Consumer<ObjectNode> mutation) {
        ObjectNode root = parseObject(json);
        mutation.accept(root);
        return toJson(root);
    }

    /** Returns the object at a JSON Pointer or fails with a useful fixture error. */
    static ObjectNode objectAt(JsonNode root, String path) {
        JsonNode node = root.at(path);
        if (!(node instanceof ObjectNode objectNode)) {
            throw new IllegalArgumentException("Fixture path is not an object: " + path);
        }
        return objectNode;
    }

    /** Returns the array at a JSON Pointer or fails with a useful fixture error. */
    static ArrayNode arrayAt(JsonNode root, String path) {
        JsonNode node = root.at(path);
        if (!(node instanceof ArrayNode arrayNode)) {
            throw new IllegalArgumentException("Fixture path is not an array: " + path);
        }
        return arrayNode;
    }

    /** Serializes a fixture tree to compact JSON accepted by the validator. */
    static String toJson(JsonNode node) {
        try {
            return MAPPER.writeValueAsString(node);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to serialize ITWG test fixture", ex);
        }
    }

    /** Converts the base XY node fixture into the equivalent LL representation. */
    private static void convertXyNodesToLl(ObjectNode root) {
        ObjectNode offset = objectAt(root, DESCRIPTION_PATH + "/path/offset");
        ObjectNode xy = objectAt(offset, "/xy");
        ArrayNode llNodes = MAPPER.createArrayNode();

        for (JsonNode node : arrayAt(xy, "/nodes")) {
            ObjectNode xyCoordinates = objectAt(node, "/delta/node-XY1");
            ObjectNode llCoordinates = MAPPER.createObjectNode()
                .put("lon", xyCoordinates.get("x").intValue())
                .put("lat", xyCoordinates.get("y").intValue());
            ObjectNode delta = MAPPER.createObjectNode();
            delta.set("node-LL1", llCoordinates);
            ObjectNode llNode = MAPPER.createObjectNode();
            llNode.set("delta", delta);
            llNodes.add(llNode);
        }

        ObjectNode ll = MAPPER.createObjectNode();
        ll.set("nodes", llNodes);
        offset.remove("xy");
        offset.set("ll", ll);
    }

    /** Sets a field on an already parsed fixture document. */
    private static void set(ObjectNode root, String fieldPath, JsonNode value) {
        parentObject(root, fieldPath).set(fieldName(fieldPath), value);
    }

    /** Returns the parent object of a field-oriented JSON Pointer. */
    private static ObjectNode parentObject(ObjectNode root, String fieldPath) {
        int separator = fieldPath.lastIndexOf('/');
        if (separator < 0) {
            throw new IllegalArgumentException("Expected a JSON Pointer: " + fieldPath);
        }
        String parentPath = separator == 0 ? "" : fieldPath.substring(0, separator);
        return objectAt(root, parentPath);
    }

    /** Extracts the final field name from a field-oriented JSON Pointer. */
    private static String fieldName(String fieldPath) {
        return fieldPath.substring(fieldPath.lastIndexOf('/') + 1);
    }

    /** Parses JSON that must contain an object. */
    private static ObjectNode parseObject(String json) {
        JsonNode node = parseJson(json);
        if (!(node instanceof ObjectNode objectNode)) {
            throw new IllegalArgumentException("Expected an object fixture");
        }
        return objectNode;
    }

    /** Parses a JSON value used by fixture mutations. */
    private static JsonNode parseJson(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Invalid JSON test value: " + json, ex);
        }
    }

    /** Loads and parses one immutable classpath fixture template. */
    private static ObjectNode loadFixture(String fileName) {
        String resourcePath = "/fixtures/itwg/" + fileName;
        try (InputStream input = ItwgTimTestFixtures.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IllegalStateException("Missing ITWG test fixture: " + resourcePath);
            }
            return (ObjectNode) MAPPER.readTree(input);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load ITWG test fixture: " + resourcePath, ex);
        }
    }

    /** Identifies the path state and offset representation used by a fixture. */
    enum NodeEncoding {
        OPEN_XY(false, "xy"),
        OPEN_LL(false, "ll"),
        CLOSED_XY(true, "xy"),
        CLOSED_LL(true, "ll");

        private final boolean closedPath;
        private final String offsetName;

        /** Stores the path state and offset representation for this fixture. */
        NodeEncoding(boolean closedPath, String offsetName) {
            this.closedPath = closedPath;
            this.offsetName = offsetName;
        }

        /** Returns whether the fixture represents a closed path. */
        boolean closedPath() {
            return closedPath;
        }

        /** Returns the JSON property containing this fixture's node list. */
        String offsetName() {
            return offsetName;
        }

        /** Returns the JSON Pointer to this fixture's first node. */
        String firstNodePath() {
            return DESCRIPTION_PATH + "/path/offset/" + offsetName + "/nodes/0";
        }
    }
}
