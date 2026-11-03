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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.util.ObjectHelper;

/**
 * This represents the schema here: https://www.stitchdata.com/docs/developers/import-api/api#message-object
 */
public final class StitchMessage implements StitchModel {
    // property names
    public static final String ACTION = "action";
    public static final String SEQUENCE = "sequence";
    public static final String DATA = "data";

    private static final Action DEFAULT_ACTION = Action.UPSERT;

    public enum Action {
        UPSERT
    };

    private final Action action;
    private final long sequence;
    private final Map<String, Object> data;

    private StitchMessage(Action action, long sequence, Map<String, Object> data) {
        this.action = action;
        this.sequence = sequence;
        this.data = data;
    }

    public static Builder builder() {
        return new Builder();
    }

    @SuppressWarnings("unchecked")
    public static Builder fromMap(final Map<String, Object> data) {
        final Action action = Action.valueOf(data.getOrDefault(ACTION, DEFAULT_ACTION.name()).toString().toUpperCase());
        final Object sequence = data.get(SEQUENCE);
        final Map<String, Object> inputData = ObjectHelper.cast(Map.class, data.getOrDefault(DATA, Collections.emptyMap()));

        final Builder builder = new Builder()
                .withAction(action)
                .withData(inputData);
        if (sequence != null) {
            // a Map read from JSON or YAML has a small number as an Integer
            builder.withSequence(sequence instanceof Number number
                    ? number.longValue() : ObjectHelper.cast(Long.class, sequence));
        }
        return builder;
    }

    public Action getAction() {
        return action;
    }

    public long getSequence() {
        return sequence;
    }

    public Map<String, Object> getData() {
        return data;
    }

    @Override
    public Map<String, Object> toMap() {
        final Map<String, Object> resultAsMap = new LinkedHashMap<>();

        resultAsMap.put(ACTION, action.name().toLowerCase());
        resultAsMap.put(SEQUENCE, sequence);
        resultAsMap.put(DATA, data);

        return resultAsMap;
    }

    public static final class Builder {
        private Action action;
        private Long sequence;
        private Map<String, Object> data = new LinkedHashMap<>();

        private Builder() {
        }

        /**
         * This will always be upsert.
         *
         * Default: upsert
         *
         * @param action
         */
        public Builder withAction(final Action action) {
            if (ObjectHelper.isNotEmpty(action)) {
                this.action = action;
            }
            return this;
        }

        /**
         * An integer that tells the Import API the order in which data points in the request body should be considered
         * for loading. This data will be stored in the destination table in the _sdc_sequence column. In other Stitch
         * integrations, Stitch uses a Unix epoch (in milliseconds) as the value for this property. Note: This value
         * cannot exceed the maximum of 9223372036854775807.
         *
         * Default: System.currentTimeMillis();
         *
         * @param sequence
         */
        public Builder withSequence(final long sequence) {
            if (ObjectHelper.isNotEmpty(sequence)) {
                this.sequence = sequence;
            }
            return this;
        }

        /**
         * The record to be upserted into a table. The record data must conform to the JSON schema contained in the
         * request’s Schema object.
         *
         * @param data
         */
        public Builder withData(final Map<String, Object> data) {
            if (ObjectHelper.isNotEmpty(data)) {
                this.data.putAll(data);
            }
            return this;
        }

        public Builder withData(final String key, final Object data) {
            if (ObjectHelper.isNotEmpty(key)) {
                this.data.put(key, data);
            }
            return this;
        }

        public StitchMessage build() {
            if (ObjectHelper.isEmpty(data)) {
                throw new IllegalArgumentException("Data cannot be empty.");
            }

            if (ObjectHelper.isEmpty(action)) {
                action = DEFAULT_ACTION;
            }

            if (ObjectHelper.isEmpty(sequence)) {
                sequence = System.currentTimeMillis();
            }

            return new StitchMessage(action, sequence, data);
        }
    }
}
