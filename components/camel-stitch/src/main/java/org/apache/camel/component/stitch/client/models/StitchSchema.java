/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.stitch.client.models;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.util.ObjectHelper;

/**
 * This represents the schema here: https://www.stitchdata.com/docs/developers/import-api/api#schema-object
 */
public final class StitchSchema implements StitchModel {

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

    @Override
    public Map<String, Object> toMap() {
        return getKeywords();
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
            if (ObjectHelper.isNotEmpty(keywords)) {
                this.keywords.putAll(keywords);
            }
            return this;
        }

        public Builder addKeyword(final String key, final Object value) {
            if (ObjectHelper.isNotEmpty(key)) {
                this.keywords.put(key, value);
            }
            return this;
        }

        public StitchSchema build() {
            return new StitchSchema(keywords);
        }
    }
}
