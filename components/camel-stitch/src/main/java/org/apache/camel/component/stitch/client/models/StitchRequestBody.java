package org.apache.camel.component.stitch.client.models;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.camel.util.ObjectHelper;

/**
 * This represents the schema here: https://www.stitchdata.com/docs/developers/import-api/api#batch-data--arguments
 */
public class StitchRequestBody {
    private final String tableName;
    private final StitchSchema schema;
    private final List<StitchMessage> messages;
    private final Set<String> keyNames;

    private StitchRequestBody(String tableName, StitchSchema schema, List<StitchMessage> messages, Set<String> keyNames) {
        this.tableName = tableName;
        this.schema = schema;
        this.messages = messages;
        this.keyNames = keyNames;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getTableName() {
        return tableName;
    }

    public StitchSchema getSchema() {
        return schema;
    }

    public List<StitchMessage> getMessages() {
        return messages;
    }

    public Set<String> getKeyNames() {
        return keyNames;
    }

    public Map<String, Object> toMap() {
        final Map<String, Object> resultAsMap = new LinkedHashMap<>();

        resultAsMap.put("table_name", tableName);
        resultAsMap.put("schema", schema.getKeywords());
        resultAsMap.put("messages", messages.stream().map(StitchMessage::toMap).collect(Collectors.toList()));
        resultAsMap.put("key_names", keyNames);

        return resultAsMap;
    }

    public static final class Builder {
        private String tableName;
        private StitchSchema schema;
        private List<StitchMessage> messages = new LinkedList<>();
        private Set<String> keyNames = new LinkedHashSet<>();

        private Builder() {
        }

        /**
         * The name of the destination table the data is being pushed to. Table names must be unique in each destination
         * schema, or loading issues will occur. REQUIRED
         *
         * @param tableName
         */
        public Builder withTableName(final String tableName) {
            this.tableName = tableName;
            return this;
        }

        /**
         * A Schema object containing the JSON schema describing the record(s) in the Message object’s data property.
         * Records must conform to this schema or an error will be returned when the request is sent. REQUIRED
         *
         * @param schema
         */
        public Builder withSchema(final StitchSchema schema) {
            this.schema = schema;
            return this;
        }

        /**
         * An array of Message objects, each representing a record to be upserted into the table. REQUIRED
         *
         * @param messages
         */
        public Builder addMessages(final List<StitchMessage> messages) {
            this.messages.addAll(messages);
            return this;
        }

        public Builder addMessage(final StitchMessage messages) {
            this.messages.add(messages);
            return this;
        }

        /**
         * An array of strings representing the Primary Key fields in the source table. Stitch use these Primary Keys to
         * de-dupe data during loading. If not provided, the table will be loaded in an append-only manner. Note: If
         * included, a value must be provided. However, it may be an empty list to indicate that the source table
         * doesn’t have a Primary Key. If fields are provided, they must adhere to the following: 1. Each field in the
         * list must be the name of a top-level property defined in the Schema object. Primary Key fields cannot be
         * contained in an object or an array. 2. Fields in the list may not be null in the source. 3. If a field is a
         * string, its value must be less than 256 characters. OPTIONAL
         *
         * @param keyNames
         */
        public Builder withKeyNames(final String... keyNames) {
            ObjectHelper.notNull(keyNames, "keyNames");

            this.keyNames.addAll(Arrays.stream(keyNames).collect(Collectors.toSet()));
            return this;
        }

        public StitchRequestBody build() {
            if (ObjectHelper.isEmpty(tableName) || ObjectHelper.isEmpty(schema) || ObjectHelper.isEmpty(messages)) {
                throw new IllegalArgumentException(
                        "One of the required arguments 'tableName', 'schema' or 'messages' is not set.");
            }
            return new StitchRequestBody(tableName, schema, messages, keyNames);
        }
    }
}
