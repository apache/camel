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
package org.apache.camel.component.azure.storage.datalake;

import java.time.Duration;
import java.util.Map;

import com.azure.storage.file.datalake.models.AccessTier;
import com.azure.storage.file.datalake.models.DataLakeRequestConditions;
import com.azure.storage.file.datalake.models.PathHttpHeaders;

public class FileCommonRequestOptions {
    private final PathHttpHeaders pathHttpHeaders;
    private final Map<String, String> metadata;
    private final AccessTier accessTier;
    private final DataLakeRequestConditions requestConditions;
    private final byte[] contentMD5;
    private final Duration timeout;

    public FileCommonRequestOptions(PathHttpHeaders pathHttpHeaders, Map<String, String> metadata, AccessTier accessTier,
                                    DataLakeRequestConditions requestConditions, byte[] contentMD5, Duration timeout) {
        this.pathHttpHeaders = pathHttpHeaders;
        this.metadata = metadata;
        this.accessTier = accessTier;
        this.requestConditions = requestConditions;
        this.contentMD5 = contentMD5;
        this.timeout = timeout;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public AccessTier getAccessTier() {
        return accessTier;
    }

    public <T extends DataLakeRequestConditions> T getRequestConditions() {
        if (requestConditions == null) {
            return null;
        }
        return (T) requestConditions;
    }

    public PathHttpHeaders getPathHttpHeaders() {
        return pathHttpHeaders;
    }

    public byte[] getContentMD5() {
        return contentMD5;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public String getLeaseId() {
        if (requestConditions == null) {
            return null;
        } else {
            return requestConditions.getLeaseId();
        }
    }
}
