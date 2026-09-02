package com.jelastic.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.Iterator;

/**
 * Reads the {@code error} member of an API answer whatever shape it takes.
 *
 * <p>Jelastic itself returns a plain string, but the gateways in front of some hosters (Infomaniak Public Cloud among
 * others) answer with a structured error such as {@code {"code":401,"message":"..."}} or with a list of errors.
 * Mapping those on a {@code String} field used to blow up the whole deserialization, hiding the real cause of the
 * failure behind a parsing error or a {@link NullPointerException}. Everything is now rendered as a readable
 * message.</p>
 */
public class ErrorMessageDeserializer extends JsonDeserializer<String> {

    private static final String[] MESSAGE_FIELDS = {
            "message", "error", "description", "detail", "details", "reason", "errorMessage", "error_description"
    };

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        return render(parser.readValueAsTree());
    }

    static String render(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }

        if (node.isValueNode()) {
            String text = node.asText();
            return text != null && text.isEmpty() ? null : text;
        }

        if (node.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode child : node) {
                String rendered = render(child);
                if (rendered == null) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append("; ");
                }
                sb.append(rendered);
            }

            return sb.length() == 0 ? null : sb.toString();
        }

        for (String field : MESSAGE_FIELDS) {
            JsonNode child = node.get(field);
            String rendered = render(child);
            if (rendered != null) {
                String code = code(node);
                return code == null ? rendered : code + " - " + rendered;
            }
        }

        // Unknown shape: keep the raw JSON rather than losing the information.
        return node.toString();
    }

    private static String code(JsonNode node) {
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if ("code".equalsIgnoreCase(name) || "status".equalsIgnoreCase(name)) {
                JsonNode value = node.get(name);
                if (value != null && value.isValueNode() && !value.asText().isEmpty()) {
                    return value.asText();
                }
            }
        }

        return null;
    }
}
