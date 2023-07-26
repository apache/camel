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
package org.apache.camel.component.file.azure;

import com.azure.storage.file.share.models.ShareFileItem;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.file.remote.RemoteFileConfiguration;
import org.apache.camel.component.file.remote.RemoteFileOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cleanup the remote contract a bit, get rid of OS-specific path separators, etc.
 */
abstract class NormalizedOperations implements RemoteFileOperations<ShareFileItem> {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final RemoteFileConfiguration configuration;

    protected NormalizedOperations(RemoteFileConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public final boolean buildDirectory(String directory, boolean absolute) throws GenericFileOperationFailedException {

        log.trace("buildDirectory({},{})", directory, absolute);

        // by observation OS-specific path separator can appear

        directory = configuration.normalizePath(directory);

        if (absolute) {
            return buildDirectory(directory);
        } else {
            // wishful thinking, we inherited a wide contract
            // but our needs are narrower. We will see....
            throw new IllegalArgumentException("Relative path: " + directory);
        }
    }

    /** Normalized form of {@link #buildDirectory(String, boolean)}. */
    protected abstract boolean buildDirectory(String path);

}
