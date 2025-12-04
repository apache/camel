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

import java.io.Serializable;
import java.time.Instant;

/**
 * Metadata about a PQC key pair including creation time, rotation schedule, and usage statistics.
 */
public class KeyMetadata implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String keyId;
    private final String algorithm;
    private final Instant createdAt;
    private Instant lastUsedAt;
    private Instant expiresAt;
    private Instant nextRotationAt;
    private long usageCount;
    private KeyStatus status;
    private String description;

    public enum KeyStatus {
        ACTIVE,
        EXPIRED,
        REVOKED,
        PENDING_ROTATION,
        DEPRECATED
    }

    public KeyMetadata(String keyId, String algorithm) {
        this.keyId = keyId;
        this.algorithm = algorithm;
        this.createdAt = Instant.now();
        this.lastUsedAt = createdAt;
        this.usageCount = 0;
        this.status = KeyStatus.ACTIVE;
    }

    public String getKeyId() {
        return keyId;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public void updateLastUsed() {
        this.lastUsedAt = Instant.now();
        this.usageCount++;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getNextRotationAt() {
        return nextRotationAt;
    }

    public void setNextRotationAt(Instant nextRotationAt) {
        this.nextRotationAt = nextRotationAt;
    }

    public long getUsageCount() {
        return usageCount;
    }

    public KeyStatus getStatus() {
        return status;
    }

    public void setStatus(KeyStatus status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isExpired() {
        return status == KeyStatus.EXPIRED
                || (expiresAt != null && Instant.now().isAfter(expiresAt));
    }

    public boolean needsRotation() {
        return status == KeyStatus.PENDING_ROTATION
                || (nextRotationAt != null && Instant.now().isAfter(nextRotationAt));
    }

    public long getAgeInDays() {
        return java.time.Duration.between(createdAt, Instant.now()).toDays();
    }

    @Override
    public String toString() {
        return String.format(
                "KeyMetadata[keyId=%s, algorithm=%s, status=%s, created=%s, age=%d days, usage=%d]",
                keyId, algorithm, status, createdAt, getAgeInDays(), usageCount);
    }
}
