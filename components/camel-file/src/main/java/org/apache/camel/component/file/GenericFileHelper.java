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
package org.apache.camel.component.file;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.function.Supplier;

import org.apache.camel.Exchange;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.util.FileUtil;

public final class GenericFileHelper {

    private GenericFileHelper() {
    }

    /**
     * Ensures the resolved local work file stays within the configured local work directory. The remote file name used
     * to build the local work file path may contain {@code ../} sequences that would otherwise resolve to a path
     * outside the work directory.
     *
     * @param  target                              the resolved local work file (or its in-progress temp file)
     * @param  localWorkDirectory                  the local work directory the file must stay within
     * @throws GenericFileOperationFailedException if the target resolves outside the local work directory
     */
    public static void jailToLocalWorkDirectory(File target, File localWorkDirectory) {
        // compact first as the remote relative name can use ../ etc
        String compactTarget = FileUtil.compactPath(target.getPath());
        String compactWork = FileUtil.compactPath(localWorkDirectory.getPath());
        if (!isWithinDirectory(compactTarget, compactWork)) {
            throw new GenericFileOperationFailedException(
                    "Cannot retrieve file to local work file: " + compactTarget
                                                          + " as it is jailed to the local work directory: "
                                                          + compactWork);
        }
        // defense-in-depth: the lexical check above cannot see symbolic links, so also resolve the existing
        // filesystem path segments (following links) and re-check. This prevents a symbolic link inside the
        // local work directory that resolves outside of it from being used to write files elsewhere. Valid
        // nested paths continue to work unchanged. Skipped when no work directory boundary is configured.
        if (!compactWork.isEmpty()) {
            try {
                Path resolvedWork = resolveExistingPathSegments(localWorkDirectory.toPath());
                Path resolvedTarget = resolveExistingPathSegments(target.toPath());
                if (!resolvedTarget.startsWith(resolvedWork)) {
                    throw new GenericFileOperationFailedException(
                            "Cannot retrieve file to local work file: " + compactTarget
                                                                  + " as it is jailed to the local work directory: "
                                                                  + compactWork);
                }
            } catch (IOException e) {
                throw new GenericFileOperationFailedException(
                        "Cannot verify local work file: " + compactTarget
                                                              + " is within the local work directory: " + compactWork,
                        e);
            }
        }
    }

    /**
     * Determines whether a local path stays within a local directory once the existing segments of both are resolved on
     * the filesystem, following symbolic links. Unlike the lexical {@link #isWithinDirectory(String, String)} this sees
     * a symbolic link placed inside the directory whose target lies outside of it. The check and a later use of the
     * path are not atomic.
     *
     * @param  target      the local path (need not exist yet)
     * @param  directory   the directory the path must stay within
     * @return             {@code true} if the resolved path is the directory itself or a path inside it
     * @throws IOException if the path cannot be resolved, for example a dangling symbolic link
     */
    public static boolean isWithinDirectoryResolvingLinks(Path target, Path directory) throws IOException {
        if (isBelowWithoutLinks(target, directory)) {
            return true;
        }
        return resolveExistingPathSegments(target).startsWith(resolveExistingPathSegments(directory));
    }

    /**
     * Whether the path is below the directory, segment by segment as written, and no existing segment below the
     * directory is a symbolic link (or another kind of link, such as a junction on Windows). Such a path resolves
     * within the directory, wherever the directory itself resolves to, so neither needs a {@link Path#toRealPath}: this
     * costs one lookup of the attributes per existing segment below the directory. A path with a {@code .} or
     * {@code ..} segment below the directory, or a segment whose attributes cannot be read, needs the links resolved.
     *
     * @param  target    the local path (need not exist yet)
     * @param  directory the directory
     * @return           {@code true} if the path is known to stay within the directory, {@code false} if the links must
     *                   be resolved to tell
     */
    static boolean isBelowWithoutLinks(Path target, Path directory) {
        return isBelowWithoutLinks(target, directory, false);
    }

    /**
     * As {@link #isBelowWithoutLinks(Path, Path)}, for a path that is expected to exist (such as a listed file), which
     * saves a lookup of the last segment.
     */
    static boolean isExistingBelowWithoutLinks(Path target, Path directory) {
        return isBelowWithoutLinks(target, directory, true);
    }

    private static boolean isBelowWithoutLinks(Path target, Path directory, boolean expectExisting) {
        final Path absoluteDirectory = directory.toAbsolutePath();
        final Path absoluteTarget = target.toAbsolutePath();
        if (!absoluteTarget.startsWith(absoluteDirectory)) {
            return false;
        }
        final int count = absoluteTarget.getNameCount();
        Path current = absoluteDirectory;
        for (int i = absoluteDirectory.getNameCount(); i < count; i++) {
            Path segment = absoluteTarget.getName(i);
            String name = segment.toString();
            if (".".equals(name) || "..".equals(name)) {
                return false;
            }
            current = current.resolve(segment);
            // the last segment of a move target usually does not exist yet, and Files.exists tells without throwing
            if (i == count - 1 && !expectExisting && !Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                return true;
            }
            BasicFileAttributes attributes;
            try {
                attributes = Files.readAttributes(current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            } catch (NoSuchFileException e) {
                // this segment does not exist, nor do the ones below it, so none of them is a link
                return true;
            } catch (IOException e) {
                return false;
            }
            if (attributes.isSymbolicLink() || attributes.isOther()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Resolves the longest existing prefix of the path on the filesystem (following symbolic links) and appends the
     * remaining, not yet existing, segments.
     *
     * @throws IOException if the path cannot be resolved, for example a dangling symbolic link
     */
    static Path resolveExistingPathSegments(Path path) throws IOException {
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

    /**
     * Determines whether a compacted target path is contained within a compacted directory path, using a path-segment
     * boundary comparison. Unlike a bare string prefix test, a sibling directory whose name merely extends the
     * directory name (for example {@code /work} versus {@code /workspace}) is not considered contained. A trailing
     * separator on the directory is tolerated, and an empty directory imposes no boundary.
     *
     * @param  compactTarget the compacted target path (see {@link FileUtil#compactPath(String)})
     * @param  compactDir    the compacted directory the target must stay within
     * @return               {@code true} if the target is the directory itself or a path inside it
     * @see                  #isWithinDirectory(String, String, char)
     */
    public static boolean isWithinDirectory(String compactTarget, String compactDir) {
        return isWithinDirectory(compactTarget, compactDir, File.separatorChar);
    }

    /**
     * Determines whether a compacted target path is contained within a compacted directory path, using the given path
     * separator. Remote file paths always use {@code /} regardless of the platform Camel runs on, so remote callers
     * must pass {@code '/'} rather than relying on {@link File#separatorChar}.
     *
     * @param  compactTarget the compacted target path (see {@link FileUtil#compactPath(String, char)})
     * @param  compactDir    the compacted directory the target must stay within
     * @param  separator     the path separator both paths are expressed with
     * @return               {@code true} if the target is the directory itself or a path inside it
     */
    public static boolean isWithinDirectory(String compactTarget, String compactDir, char separator) {
        // a target that still resolves upwards after compaction escapes any root, even when no boundary is
        // configured, so it is never contained
        if (compactTarget.equals("..") || compactTarget.startsWith(".." + separator)) {
            return false;
        }
        if (compactDir.isEmpty()) {
            // no directory boundary configured
            return true;
        }
        // drop a trailing separator (if any) so the boundary comparison is exact, regardless of whether the
        // directory path was supplied with or without one
        String dir = compactDir;
        if (dir.charAt(dir.length() - 1) == separator) {
            dir = dir.substring(0, dir.length() - 1);
        }
        return compactTarget.equals(dir) || compactTarget.startsWith(dir + separator);
    }

    public static String asExclusiveReadLockKey(GenericFile file, String key) {
        // use the copy from absolute path as that was the original path of the
        // file when the lock was acquired
        // for example if the file consumer uses preMove then the file is moved
        // and therefore has another name
        // that would no longer match
        String path
                = file.getCopyFromAbsoluteFilePath() != null ? file.getCopyFromAbsoluteFilePath() : file.getAbsoluteFilePath();
        return asExclusiveReadLockKey(path, key);
    }

    public static String asExclusiveReadLockKey(String path, String key) {
        return path + "-" + key;
    }

    public static <T> Exchange createDummy(GenericFileEndpoint<T> endpoint, Exchange dynamic, Supplier<GenericFile<T>> file) {
        Exchange dummy = endpoint.createExchange(file.get());
        enrichFromDynamic(dummy, dynamic);
        return dummy;
    }

    public static <T> Exchange createDummy(GenericFileEndpoint<T> endpoint, Exchange dynamic) {
        Exchange dummy = endpoint.createExchange();
        enrichFromDynamic(dummy, dynamic);
        return dummy;
    }

    private static void enrichFromDynamic(Exchange dummy, Exchange dynamic) {
        if (dynamic != null) {
            if (dynamic.getMessage().hasHeaders()) {
                MessageHelper.copyHeaders(dynamic.getMessage(), dummy.getMessage(), true);
            }
            if (dynamic.hasVariables()) {
                dummy.getVariables().putAll(dynamic.getVariables());
            }
            if (dynamic.hasProperties()) {
                dummy.getProperties().putAll(dynamic.getProperties());
            }
        }
    }

}
