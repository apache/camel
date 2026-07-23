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
package org.apache.camel.component.azure.common;

import java.io.File;
import java.nio.file.Path;

/**
 * Utility methods for safely handling local file paths derived from remote Azure Storage object names.
 */
public final class AzureFileNameHelper {

    private AzureFileNameHelper() {
    }

    /**
     * Resolves a remote object {@code name} against the configured local {@code fileDir}, ensuring the resulting file
     * stays within that directory. A remote object name is influenced by whoever writes to the storage container and
     * may contain {@code ../} segments that would otherwise resolve to a location outside {@code fileDir}.
     *
     * @param  fileDir                  the configured local directory the download must stay within
     * @param  name                     the remote object name used to build the local file
     * @return                          the resolved local {@link File}, guaranteed to be within {@code fileDir}
     * @throws IllegalArgumentException if the resolved file would be located outside {@code fileDir}
     */
    public static File resolveWithinDirectory(String fileDir, String name) {
        final File target = new File(fileDir, name);
        // normalize lexically (removes ./ and ../ segments) and compare on path-segment boundaries so a sibling
        // directory whose name merely extends fileDir is not considered contained
        final Path normalizedDir = new File(fileDir).toPath().normalize();
        final Path normalizedTarget = target.toPath().normalize();
        if (!normalizedTarget.startsWith(normalizedDir)) {
            throw new IllegalArgumentException(
                    "Cannot download to file '" + name
                                               + "' as it resolves outside the configured fileDir directory: " + fileDir);
        }
        return target;
    }
}
