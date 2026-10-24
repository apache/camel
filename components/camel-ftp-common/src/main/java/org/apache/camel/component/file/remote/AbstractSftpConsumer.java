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
package org.apache.camel.component.file.remote;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.function.Supplier;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileHelper;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.file.GenericFileProcessStrategy;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.function.Suppliers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for SFTP consumers, shared between JSch and MINA SSHD implementations.
 */
public abstract class AbstractSftpConsumer extends RemoteFileConsumer<SftpRemoteFile> {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractSftpConsumer.class);

    /**
     * The most symbolic links followed to resolve a path, as a longer chain is most likely a loop
     */
    private static final int MAX_SYMBOLIC_LINKS = 32;

    protected String endpointPath;

    // the starting directory with its symbolic links resolved on the server, computed once per poll
    private volatile String resolvedStartingDirectory;

    protected AbstractSftpConsumer(RemoteFileEndpoint<SftpRemoteFile> endpoint, Processor processor,
                                   RemoteFileOperations<SftpRemoteFile> operations,
                                   GenericFileProcessStrategy<SftpRemoteFile> processStrategy) {
        super(endpoint, processor, operations, processStrategy);
        this.endpointPath = endpoint.getConfiguration().getDirectory();
    }

    @Override
    protected void doStart() throws Exception {
        // turn off scheduler first, so autoCreate is handled before scheduler starts
        boolean startScheduler = isStartScheduler();
        setStartScheduler(false);
        try {
            super.doStart();
            if (endpoint.isAutoCreate() && hasStartingDirectory()) {
                String dir = endpoint.getConfiguration().getDirectory();
                LOG.debug("Auto creating directory: {}", dir);
                try {
                    connectIfNecessary();
                    operations.buildDirectory(dir, true);
                } catch (GenericFileOperationFailedException e) {
                    // log a WARN as we want to start the consumer.
                    LOG.warn(
                            "Error auto creating directory: " + dir + " due " + e.getMessage() + ". This exception is ignored.",
                            e);
                }
            }
        } finally {
            if (startScheduler) {
                setStartScheduler(true);
                startScheduler();
            }
        }
    }

    @Override
    protected boolean pollDirectory(Exchange dynamic, String fileName, List<GenericFile<SftpRemoteFile>> fileList, int depth) {
        // resolved again on the first symbolic link of this poll, as the starting directory can be replaced between polls
        resolvedStartingDirectory = null;

        String currentDir = null;
        if (isStepwise()) {
            // must remember current dir so we stay in that directory after the poll
            currentDir = operations.getCurrentDirectory();
        }

        // strip trailing slash
        fileName = FileUtil.stripTrailingSeparator(fileName);

        boolean answer = doPollDirectory(dynamic, fileName, null, fileList, depth);
        if (currentDir != null) {
            operations.changeCurrentDirectory(currentDir);
        }

        return answer;
    }

    protected boolean pollSubDirectory(
            Exchange dynamic,
            String absolutePath, String dirName, List<GenericFile<SftpRemoteFile>> fileList, int depth) {
        boolean answer = doSafePollSubDirectory(dynamic, absolutePath, dirName, fileList, depth);
        // change back to parent directory when finished polling sub directory
        if (isStepwise()) {
            operations.changeToParentDirectory();
        }
        return answer;
    }

    @Override
    protected boolean doPollDirectory(
            Exchange dynamic,
            String absolutePath, String dirName, List<GenericFile<SftpRemoteFile>> fileList, int depth) {
        LOG.trace("doPollDirectory from absolutePath: {}, dirName: {}", absolutePath, dirName);

        depth++;

        // remove trailing /
        dirName = FileUtil.stripTrailingSeparator(dirName);

        // compute dir depending on stepwise is enabled or not
        String dir;
        if (isStepwise()) {
            dir = ObjectHelper.isNotEmpty(dirName) ? dirName : absolutePath;
            operations.changeCurrentDirectory(dir);
        } else {
            dir = absolutePath;
        }

        final SftpRemoteFile[] files = getSftpRemoteFiles(dynamic, dir);

        if (files == null || files.length == 0) {
            // no files in this directory to poll
            LOG.trace("No files found in directory: {}", dir);
            return true;
        } else {
            // we found some files
            LOG.trace("Found {} files in directory: {}", files.length, dir);
        }

        if (getEndpoint().isPreSort()) {
            Arrays.sort(files, preSortComparator(getEndpoint().getPreSort()));
        }

        for (SftpRemoteFile file : files) {

            if (LOG.isTraceEnabled()) {
                LOG.trace("SftpFile[fileName={}, longName={}, dir={}]", file.getFilename(), file.getLongname(),
                        file.isDirectory());
            }

            // check if we can continue polling in files
            if (!canPollMoreFiles(fileList)) {
                return false;
            }

            if (file.isDirectory()) {
                if (endpoint.isRecursive() && depth < endpoint.getMaxDepth()) {
                    String absoluteFilePath = FtpUtils.absoluteFilePath(absolutePath, file.getFilename());
                    Supplier<GenericFile<SftpRemoteFile>> remote
                            = Suppliers.memorize(
                                    () -> asRemoteFile(absolutePath, absoluteFilePath, file, getEndpoint().getCharset()));
                    Supplier<String> relativePath = getRelativeFilePath(endpointPath, null, absolutePath, file);
                    if (isValidFile(dynamic, remote, file.getFilename(), absoluteFilePath, relativePath, true, files)) {
                        // recursive scan and add the sub files and folders
                        String subDirectory = file.getFilename();
                        String path = ObjectHelper.isNotEmpty(absolutePath) ? absolutePath + "/" + subDirectory : subDirectory;
                        boolean canPollMore = pollSubDirectory(dynamic, path, subDirectory, fileList, depth);
                        if (!canPollMore) {
                            return false;
                        }
                    }
                }
            } else {
                if (depth >= endpoint.getMinDepth()) {
                    String absoluteFilePath = FtpUtils.absoluteFilePath(absolutePath, file.getFilename());
                    Supplier<GenericFile<SftpRemoteFile>> remote
                            = Suppliers.memorize(
                                    () -> asRemoteFile(absolutePath, absoluteFilePath, file, getEndpoint().getCharset()));
                    Supplier<String> relativePath = getRelativeFilePath(endpointPath, null, absolutePath, file);
                    if (isValidFile(dynamic, remote, file.getFilename(), absoluteFilePath, relativePath, false, files)) {
                        // matched file so add
                        fileList.add(remote.get());
                    }
                }
            }
        }

        return true;
    }

    private SftpRemoteFile[] listFiles(String dir) {
        if (isStepwise()) {
            return operations.listFiles();
        }

        return operations.listFiles(dir);
    }

    private SftpRemoteFile[] getSftpRemoteFiles(Exchange dynamic, String dir) {
        SftpRemoteFile[] files = null;
        try {
            LOG.trace("Polling directory: {}", dir);
            if (isUseList()) {
                files = listFiles(dir);
            } else {
                files = pollNamedFile(dynamic);
            }
        } catch (GenericFileOperationFailedException e) {
            if (ignoreCannotRetrieveFile(null, null, e)) {
                LOG.debug("Cannot list files in directory {} due directory does not exist or file permission error.", dir);
            } else {
                throw e;
            }
        }
        return files;
    }

    private SftpRemoteFile[] pollNamedFile(Exchange dynamic) {
        SftpRemoteFile[] files = null;

        // we cannot use the LIST command(s) so we can only poll a named
        // file so created a pseudo file with that name
        Exchange dummy = GenericFileHelper.createDummy(getEndpoint(), dynamic);
        String name = evaluateFileExpression(dummy);
        if (name != null) {
            SftpRemoteFile file = new SftpRemoteFileSingle(name);
            files = new SftpRemoteFile[1];
            files[0] = file;
        }

        return files;
    }

    @Override
    protected boolean isMatched(Supplier<GenericFile<SftpRemoteFile>> file, String doneFileName, SftpRemoteFile[] files) {
        String onlyName = FileUtil.stripPath(doneFileName);

        for (SftpRemoteFile f : files) {
            if (f.getFilename().equals(onlyName)) {
                return true;
            }
        }

        LOG.trace("Done file: {} does not exist", doneFileName);
        return false;
    }

    @Override
    protected boolean isWithinStartingDirectory(Supplier<GenericFile<SftpRemoteFile>> file, String absoluteFilePath) {
        if (!super.isWithinStartingDirectory(file, absoluteFilePath)) {
            return false;
        }
        GenericFile<SftpRemoteFile> remoteFile = file.get();
        if (remoteFile.getFile() == null || !remoteFile.getFile().isSymbolicLink()) {
            return true;
        }
        // the server follows a symbolic link when the file is retrieved, so the target of a listed link must be within
        // the starting directory too
        if (!(operations instanceof SftpSymbolicLinkOperations links)) {
            LOG.debug("Cannot resolve symbolic link: {} as the operations cannot read symbolic links", absoluteFilePath);
            return false;
        }
        try {
            String startingDirectory = resolvedStartingDirectory;
            if (startingDirectory == null) {
                startingDirectory = resolveSymbolicLinks(links, null, endpointPath);
                resolvedStartingDirectory = startingDirectory;
            }
            // the path of the file below the starting directory is resolved from the resolved starting directory
            String relativeFilePath = remoteFile.getRelativeFilePath();
            String target = relativeFilePath != null
                    ? resolveSymbolicLinks(links, startingDirectory, relativeFilePath)
                    : resolveSymbolicLinks(links, null, remoteFile.getAbsoluteFilePath());
            return GenericFileHelper.isWithinDirectory(target, startingDirectory, '/');
        } catch (GenericFileOperationFailedException e) {
            // such as a dangling symbolic link
            LOG.debug("Cannot resolve symbolic link: {} against the starting directory due to: {}", absoluteFilePath,
                    e.getMessage());
            return false;
        }
    }

    /**
     * Resolves a path on the server the way the server does when it opens the path: one segment at a time, replacing
     * each symbolic link with its target, which is relative to the directory of the link unless it is absolute. The
     * realpath request is not used for this, as some servers (such as Apache MINA SSHD) do not resolve links in it.
     *
     * @param  operations the operations to look up the segments with
     * @param  directory  the absolute path, without symbolic links, that a relative path is resolved against, or
     *                    {@code null} for the home directory
     * @param  path       the path to resolve
     * @return            the absolute path, without symbolic links and without {@code .} and {@code ..} segments
     */
    private static String resolveSymbolicLinks(SftpSymbolicLinkOperations operations, String directory, String path) {
        String name = path != null ? path : "";
        Deque<String> resolved = new ArrayDeque<>();
        Deque<String> pending = new ArrayDeque<>();
        if (!name.startsWith("/")) {
            if (directory != null) {
                // the directory has no links to resolve
                for (String segment : directory.split("/")) {
                    if (!segment.isEmpty()) {
                        resolved.addLast(segment);
                    }
                }
            } else {
                pending.addAll(Arrays.asList(operations.getHomeDirectory().split("/")));
            }
        }
        pending.addAll(Arrays.asList(name.split("/")));

        int links = 0;
        while (!pending.isEmpty()) {
            String segment = pending.removeFirst();
            if (segment.isEmpty() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                resolved.pollLast();
                continue;
            }
            String candidate = "/" + (resolved.isEmpty() ? "" : String.join("/", resolved) + "/") + segment;
            if (operations.isSymbolicLink(candidate)) {
                if (++links > MAX_SYMBOLIC_LINKS) {
                    throw new GenericFileOperationFailedException("Too many levels of symbolic links: " + name);
                }
                String target = operations.readSymbolicLink(candidate);
                if (ObjectHelper.isEmpty(target)) {
                    throw new GenericFileOperationFailedException("Symbolic link without a target: " + candidate);
                }
                if (target.startsWith("/")) {
                    resolved.clear();
                }
                // the segments of the target take the place of the link
                String[] segments = target.split("/");
                for (int i = segments.length - 1; i >= 0; i--) {
                    pending.addFirst(segments[i]);
                }
            } else {
                resolved.addLast(segment);
            }
        }
        return "/" + String.join("/", resolved);
    }

    @Override
    protected Supplier<String> getRelativeFilePath(String endpointPath, String path, String absolutePath, SftpRemoteFile file) {
        return () -> {
            String relativePath = StringHelper.after(absolutePath, endpointPath);
            // skip trailing /
            return FileUtil.stripLeadingSeparator(relativePath != null ? relativePath + "/" : "") + file.getFilename();
        };
    }

    private RemoteFile<SftpRemoteFile> asRemoteFile(
            String absolutePath, String absoluteFilePath, SftpRemoteFile file, String charset) {
        RemoteFile<SftpRemoteFile> answer = new RemoteFile<>();

        answer.setCharset(charset);
        answer.setEndpointPath(endpointPath);
        answer.setFile(file);
        answer.setFileNameOnly(file.getFilename());
        answer.setFileLength(file.getFileLength());
        answer.setLastModified(file.getLastModified());
        answer.setHostname(((RemoteFileConfiguration) endpoint.getConfiguration()).getHost());
        answer.setDirectory(file.isDirectory());

        // absolute or relative path
        boolean absolute = FileUtil.hasLeadingSeparator(absolutePath);
        answer.setAbsolute(absolute);
        answer.setAbsoluteFilePath(absoluteFilePath);

        // the relative filename, skip the leading endpoint configured path
        String relativePath = StringHelper.after(absoluteFilePath, endpointPath);
        // skip trailing /
        relativePath = FileUtil.stripLeadingSeparator(relativePath);
        answer.setRelativeFilePath(relativePath);

        // the file name should be the relative path
        answer.setFileName(answer.getRelativeFilePath());

        return answer;
    }

    private boolean isStepwise() {
        RemoteFileConfiguration config = (RemoteFileConfiguration) endpoint.getConfiguration();
        return config.isStepwise();
    }

    private boolean isUseList() {
        RemoteFileConfiguration config = (RemoteFileConfiguration) endpoint.getConfiguration();
        return config.isUseList();
    }

    @SuppressWarnings("unchecked")
    private static Comparator<SftpRemoteFile> preSortComparator(String preSort) {
        boolean reverse = preSort.startsWith("-");
        String field = reverse ? preSort.substring(1) : preSort;
        Comparator<SftpRemoteFile> cmp;
        switch (field) {
            case "modified":
                cmp = Comparator.comparingLong(SftpRemoteFile::getLastModified);
                break;
            case "size":
                cmp = Comparator.comparingLong(SftpRemoteFile::getFileLength);
                break;
            default:
                cmp = Comparator.comparing(SftpRemoteFile::getFilename);
                break;
        }
        return reverse ? cmp.reversed() : cmp;
    }
}
