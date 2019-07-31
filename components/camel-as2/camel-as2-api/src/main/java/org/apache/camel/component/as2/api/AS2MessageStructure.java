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
package org.apache.camel.component.as2.api;

public enum AS2MessageStructure {
    PLAIN(false, false, false),
    SIGNED(true, false, false),
    ENCRYPTED(false, true, false),
    SIGNED_ENCRYPTED(true, true, false),
    PLAIN_COMPRESSED(false, false, true),
    SIGNED_COMPRESSED(true, false, true),
    ENCRYPTED_COMPRESSED(false, true, true),
    ENCRYPTED_COMPRESSED_SIGNED(true, true, true);

    private final boolean isSigned;
    private final boolean isEncrypted;
    private final boolean isCompressed;

    private AS2MessageStructure(boolean isSigned, boolean isEncrypted, boolean isCompressed) {
        this.isSigned = isSigned;
        this.isEncrypted = isEncrypted;
        this.isCompressed = isCompressed;
    }

    public boolean isSigned() {
        return isSigned;
    }
    public boolean isEncrypted() {
        return isEncrypted;
    }
    public boolean isCompressed() {
        return isCompressed;
    }
}
