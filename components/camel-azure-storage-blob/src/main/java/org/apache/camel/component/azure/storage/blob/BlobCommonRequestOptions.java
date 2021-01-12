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
package org.apache.camel.component.azure.storage.blob;

import java.time.Duration;
import java.util.Map;

import com.azure.storage.blob.models.AccessTier;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobRequestConditions;

public class BlobCommonRequestOptions {

    private final BlobHttpHeaders blobHttpHeaders;
    private final Map<String, String> metadata;
    private final AccessTier accessTier;
    private final BlobRequestConditions blobRequestConditions;
    private final byte[] contentMD5;
    private final Duration timeout;

    public BlobCommonRequestOptions(BlobHttpHeaders blobHttpHeaders, Map<String, String> metadata, AccessTier accessTier,
                                    BlobRequestConditions blobRequestConditions, byte[] contentMD5, Duration timeout) {
        this.blobHttpHeaders = blobHttpHeaders;
        this.metadata = metadata;
        this.accessTier = accessTier;
        this.blobRequestConditions = blobRequestConditions;
        this.contentMD5 = contentMD5;
        this.timeout = timeout;
    }

    public BlobHttpHeaders getBlobHttpHeaders() {
        return blobHttpHeaders;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public AccessTier getAccessTier() {
        return accessTier;
    }

    @SuppressWarnings("unchecked")
    public <T extends BlobRequestConditions> T getBlobRequestConditions() {
        return blobRequestConditions == null ? null : (T) blobRequestConditions;
    }

    public byte[] getContentMD5() {
        return contentMD5;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public String leaseId() {
        return blobRequestConditions != null ? blobRequestConditions.getLeaseId() : null;
    }
}
