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
package org.apache.camel.component.pqc.lifecycle;

import java.io.ObjectInputFilter;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Shared helpers for persisting {@link KeyMetadata} as JSON instead of Java serialization, and for safely reading
 * values written by older versions that used Java serialization.
 * <p>
 * {@link AwsSecretsManagerKeyLifecycleManager} and {@link HashicorpVaultKeyLifecycleManager} store key metadata as
 * JSON, consistent with {@link FileBasedKeyLifecycleManager}. Values written by older versions (a Base64-encoded,
 * Java-serialized {@link KeyMetadata}) are still read for backward compatibility, but the deserialization is
 * constrained to the expected types through an {@link ObjectInputFilter}.
 */
final class KeyMetadataCodec {

    private static final String METADATA_PATTERN
            = "maxdepth=20;java.lang.*;java.time.**;org.apache.camel.component.pqc.lifecycle.*;!*";

    private static final String KEY_PAIR_PATTERN
            = "maxdepth=20;java.lang.*;java.util.*;java.time.**;java.security.**;javax.crypto.**;"
              + "org.apache.camel.component.pqc.lifecycle.*;org.bouncycastle.**;!*";

    /**
     * Allow-list filter for reading a legacy Java-serialized {@link KeyMetadata}. Only the JDK types that make up a
     * {@code KeyMetadata} (its {@link Instant} timestamps and {@link KeyMetadata.KeyStatus} enum) and the metadata
     * class itself are permitted; everything else is rejected.
     */
    static final ObjectInputFilter METADATA_FILTER = ObjectInputFilter.Config.createFilter(METADATA_PATTERN);

    /**
     * Allow-list filter for reading a legacy Java-serialized {@link java.security.KeyPair}. In addition to the metadata
     * types it permits the JDK and Bouncy Castle key classes required to reconstruct a key pair; everything else is
     * rejected.
     */
    static final ObjectInputFilter KEY_PAIR_FILTER = ObjectInputFilter.Config.createFilter(KEY_PAIR_PATTERN);

    private static final ObjectMapper MAPPER
            = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private KeyMetadataCodec() {
    }

    /**
     * Whether the stored value is the JSON representation (new format) rather than a Base64-encoded, Java-serialized
     * value (legacy format).
     */
    static boolean isJson(String value) {
        return value != null && value.stripLeading().startsWith("{");
    }

    /**
     * Serializes the given metadata to its JSON representation.
     */
    static String toJson(KeyMetadata metadata) throws Exception {
        return MAPPER.writeValueAsString(Data.from(metadata));
    }

    /**
     * Parses metadata from its JSON representation.
     */
    static KeyMetadata fromJson(String json) throws Exception {
        return MAPPER.readValue(json, Data.class).toKeyMetadata();
    }

    /**
     * JSON structure mirroring {@link KeyMetadata}, matching the representation already used by
     * {@link FileBasedKeyLifecycleManager}.
     */
    static final class Data {
        @JsonProperty("keyId")
        String keyId;
        @JsonProperty("algorithm")
        String algorithm;
        @JsonProperty("createdAt")
        String createdAt;
        @JsonProperty("lastUsedAt")
        String lastUsedAt;
        @JsonProperty("expiresAt")
        String expiresAt;
        @JsonProperty("nextRotationAt")
        String nextRotationAt;
        @JsonProperty("usageCount")
        long usageCount;
        @JsonProperty("status")
        String status;
        @JsonProperty("description")
        String description;

        Data() {
        }

        static Data from(KeyMetadata metadata) {
            Data data = new Data();
            data.keyId = metadata.getKeyId();
            data.algorithm = metadata.getAlgorithm();
            data.createdAt = metadata.getCreatedAt().toString();
            data.lastUsedAt = metadata.getLastUsedAt() != null ? metadata.getLastUsedAt().toString() : null;
            data.expiresAt = metadata.getExpiresAt() != null ? metadata.getExpiresAt().toString() : null;
            data.nextRotationAt = metadata.getNextRotationAt() != null ? metadata.getNextRotationAt().toString() : null;
            data.usageCount = metadata.getUsageCount();
            data.status = metadata.getStatus().name();
            data.description = metadata.getDescription();
            return data;
        }

        KeyMetadata toKeyMetadata() {
            KeyMetadata metadata = new KeyMetadata(keyId, algorithm, Instant.parse(createdAt));
            if (lastUsedAt != null) {
                metadata.setLastUsedAt(Instant.parse(lastUsedAt));
            }
            if (expiresAt != null) {
                metadata.setExpiresAt(Instant.parse(expiresAt));
            }
            if (nextRotationAt != null) {
                metadata.setNextRotationAt(Instant.parse(nextRotationAt));
            }
            metadata.setUsageCount(usageCount);
            metadata.setStatus(KeyMetadata.KeyStatus.valueOf(status));
            metadata.setDescription(description);
            return metadata;
        }
    }
}
