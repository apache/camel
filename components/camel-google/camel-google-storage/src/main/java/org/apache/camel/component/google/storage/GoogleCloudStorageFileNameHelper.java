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
     * Rejects a remote object name that could steer a local download path out of the directory it is resolved in,
     * whatever the configured {@code downloadFileName} looks like. That is an absolute object name (one starting with
     * {@code /} or {@code \}, or with a drive letter such as {@code C:}) or an object name with a {@code ..} path
     * segment, even one that would normalize back inside the directory. Both {@code /} and {@code \} are treated as
     * separators so the check does not depend on the platform the consumer runs on.
     * <p>
     * This is what confines a fully dynamic {@code downloadFileName} such as {@code ${file:name}}, which has no
     * configured directory to check the resolved path against.
     *
     * @param  objectName               the remote object name
     * @throws IllegalArgumentException if the object name is absolute or has a {@code ..} path segment
     */
    static void assertSafeObjectName(String objectName) {
        if (objectName.startsWith("/") || objectName.startsWith("\\") || hasDriveLetter(objectName)) {
            throw new IllegalArgumentException(
                    "Cannot download to file '" + objectName + "' as the object name is an absolute path");
        }
        for (String segment : objectName.split("[/\\\\]")) {
            if ("..".equals(segment)) {
                throw new IllegalArgumentException(
                        "Cannot download to file '" + objectName + "' as the object name has a '..' path segment");
            }
        }
    }

    /**
     * Verifies that a relative local download path does not climb out of the working directory. This applies to a fully
     * dynamic {@code downloadFileName} such as {@code ${file:name}}, which has no configured directory to confine the
     * download to. {@link #assertSafeObjectName(String)} already keeps the object name itself from climbing out; this
     * catches an object name that joins with the configured text into a parent segment, for example
     * {@code .${file:name}} with an object named {@code ./file.txt}, which resolves to {@code ../file.txt}.
     * <p>
     * The check is lexical on purpose: an absolute path comes from the route author's own configuration, and symbolic
     * links inside the working directory belong to the deployment, not to the remote object name.
     *
     * @param  resolvedPath             the resolved local path
     * @param  objectName               the remote object name used to build the local path, for error reporting
     * @throws IllegalArgumentException if the relative path resolves outside the working directory
     */
    static void assertWithinWorkingDirectory(String resolvedPath, String objectName) {
        final Path normalized = new File(resolvedPath).toPath().normalize();
        if (!normalized.isAbsolute() && normalized.startsWith("..")) {
            throw new IllegalArgumentException(
                    "Cannot download to file '" + objectName + "' as it resolves outside the working directory: "
                                               + resolvedPath);
        }
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

    /**
     * Extracts the static directory prefix of a configured {@code downloadFileName} that contains an expression token,
     * that is the part before the first {@code $} trimmed back to the last path separator. Trimming back to a separator
     * is required so that a partial path segment is not mistaken for a directory: {@code /tmp/down${file:name}} has
     * {@code /tmp} as its static directory prefix, not {@code /tmp/down}.
     *
     * @param  downloadFileName the configured {@code downloadFileName} containing at least one expression token
     * @return                  the static directory prefix, or an empty string when the configured value is fully
     *                          dynamic and has no static directory prefix (for example {@code ${file:name}})
     */
    static String staticDirectoryPrefix(String downloadFileName) {
        final String beforeExpression = downloadFileName.substring(0, downloadFileName.indexOf('$'));
        final int lastSeparator = Math.max(beforeExpression.lastIndexOf('/'), beforeExpression.lastIndexOf('\\'));
        if (lastSeparator < 0) {
            return "";
        }
        if (lastSeparator == 0) {
            // the prefix is the filesystem root itself
            return beforeExpression.substring(0, 1);
        }
        if (lastSeparator == 2 && hasDriveLetter(beforeExpression)) {
            // the prefix is a Windows drive root such as C:\ - keep the separator, as C: alone is drive-relative (the
            // current directory on that drive) rather than the root
            return beforeExpression.substring(0, 3);
        }
        return beforeExpression.substring(0, lastSeparator);
    }

    private static boolean hasDriveLetter(String path) {
        return path.length() >= 2 && path.charAt(1) == ':' && Character.isLetter(path.charAt(0));
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
