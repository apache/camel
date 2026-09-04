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
package org.apache.camel.processor.keyvalue.jpa;

import java.io.Serial;
import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/**
 * JPA entity representing a single key-value entry in the {@code CAMEL_KEYVALUE} table.
 * <p/>
 * The primary key is the {@link #getItemKey() itemKey} (a logical string key). The value is stored as a serialized byte
 * array and an optional expiration timestamp controls TTL semantics.
 *
 * @since 4.23
 */
@Entity
@Table(name = "CAMEL_KEYVALUE")
public class KeyValueEntry implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String itemKey;
    private byte[] itemValue;
    private long expiresAt;

    /**
     * Default constructor required by JPA.
     */
    public KeyValueEntry() {
    }

    /**
     * Creates a new key-value entry.
     *
     * @param itemKey   the logical key
     * @param itemValue the serialized value
     * @param expiresAt the expiration timestamp in epoch milliseconds; {@code 0} means the entry never expires
     */
    public KeyValueEntry(String itemKey, byte[] itemValue, long expiresAt) {
        this.itemKey = itemKey;
        this.itemValue = itemValue;
        this.expiresAt = expiresAt;
    }

    @Id
    @Column(name = "ITEM_KEY", length = 512)
    public String getItemKey() {
        return itemKey;
    }

    public void setItemKey(String itemKey) {
        this.itemKey = itemKey;
    }

    @Lob
    @Column(name = "ITEM_VALUE", nullable = false)
    public byte[] getItemValue() {
        return itemValue;
    }

    public void setItemValue(byte[] itemValue) {
        this.itemValue = itemValue;
    }

    @Column(name = "EXPIRES_AT", nullable = false)
    public long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(long expiresAt) {
        this.expiresAt = expiresAt;
    }

    /**
     * Returns {@code true} if this entry has a non-zero expiration time that is in the past.
     *
     * @return whether the entry is expired
     */
    public boolean isExpired() {
        return expiresAt > 0 && System.currentTimeMillis() >= expiresAt;
    }

    @Override
    public String toString() {
        return "KeyValueEntry[key=" + itemKey + ", expiresAt=" + expiresAt + "]";
    }
}
