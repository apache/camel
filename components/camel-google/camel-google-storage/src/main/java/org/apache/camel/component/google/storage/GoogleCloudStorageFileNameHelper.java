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
package org.apache.camel.component.google.storage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/**
 * Utility methods for safely handling local file paths derived from remote Google Cloud Storage object names.
 */
final class GoogleCloudStorageFileNameHelper {

    private GoogleCloudStorageFileNameHelper() {
    }

    /**
     * Verifies that a local download path built from a remote object name stays within the configured download
     * directory. A remote object name is influenced by whoever writes to the bucket and may contain path segments that
     * would otherwise resolve to a location outside the download directory.
     * <p>
     * Object names are not stripped of their path component on purpose: Google Cloud Storage object names commonly use
     * {@code /} as a pseudo-directory separator, so stripping would flatten nested names and could make distinct
     * objects collide on the same local file.
     *
     * @param  downloadDirectory        the configured local directory the download must stay within
     * @param  resolvedPath             the resolved local path built from {@code downloadDirectory} and the object name
     * @param  objectName               the remote object name used to build the local path, for error reporting
     * @throws IllegalArgumentException if the resolved path is located outside {@code downloadDirectory}
     */
    static void assertWithinDirectory(String downloadDirectory, String resolvedPath, String objectName) {
        // normalize lexically (removes ./ and ../ segments) and compare on path-segment boundaries so a sibling
        // directory whose name merely extends downloadDirectory is not considered contained
        final Path normalizedDir = new File(downloadDirectory).toPath().normalize();
        final Path normalizedTarget = new File(resolvedPath).toPath().normalize();
        if (!normalizedTarget.startsWith(normalizedDir)) {
            throw outsideDirectory(objectName, downloadDirectory);
        }

        try {
            final Path resolvedDir = resolveExistingPathSegments(new File(downloadDirectory).toPath());
            final Path resolvedTarget = resolveExistingPathSegments(new File(resolvedPath).toPath());
            if (!resolvedTarget.startsWith(resolvedDir)) {
                throw outsideDirectory(objectName, downloadDirectory);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "Cannot verify download path for file '" + objectName
                                               + "' within the configured downloadFileName directory: "
                                               + downloadDirectory,
                    e);
        }
    }

    private static Path resolveExistingPathSegments(Path path) throws IOException {
        // Preserve the raw path segments here. Normalizing before resolving links changes the filesystem meaning of
        // paths such as link/../file when link points to another directory.
        final Path absolutePath = path.toAbsolutePath();
        Path existingPath = absolutePath;
        while (existingPath != null && !Files.exists(existingPath, LinkOption.NOFOLLOW_LINKS)) {
            existingPath = existingPath.getParent();
        }
        if (existingPath == null) {
            throw new IOException("No existing ancestor found for " + path);
        }

        final Path resolvedExistingPath = existingPath.toRealPath();
        return resolvedExistingPath.resolve(existingPath.relativize(absolutePath)).normalize();
    }

    private static IllegalArgumentException outsideDirectory(String objectName, String downloadDirectory) {
        return new IllegalArgumentException(
                "Cannot download to file '" + objectName
                                            + "' as it resolves outside the configured downloadFileName directory: "
                                            + downloadDirectory);
    }
}
