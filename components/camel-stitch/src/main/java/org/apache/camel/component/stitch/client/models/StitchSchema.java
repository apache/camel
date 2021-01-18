package org.apache.camel.component.stitch.client.models;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * This represents the schema here: https://www.stitchdata.com/docs/developers/import-api/api#schema-object
 */
public class StitchSchema {

    private final Map<String, Object> keywords;

    private StitchSchema(Map<String, Object> keywords) {
        this.keywords = keywords;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Map<String, Object> getKeywords() {
        return keywords;
    }

    public static final class Builder {
        private Map<String, Object> keywords = new LinkedHashMap<>();

        private Builder() {
        }

        /**
         * The JSON schema that records in the data property must conform to. Refer to the JSON schema docs for more
         * info about JSON schemas.
         */
        public Builder addKeywords(final Map<String, Object> keywords) {
            this.keywords.putAll(keywords);
            return this;
        }

        public Builder addKeyword(final String key, final Object value) {
            this.keywords.put(key, value);
            return this;
        }

        public StitchSchema build() {
            return new StitchSchema(keywords);
        }
    }
}
