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
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.file.consumer.DirectoryEntriesResumeAdapter;
import org.apache.camel.component.file.consumer.FileOffsetResumeAdapter;
import org.apache.camel.component.file.consumer.adapters.DirectoryEntries;
import org.apache.camel.resume.ResumeAdapter;
import org.apache.camel.resume.ResumeAware;
import org.apache.camel.resume.ResumeStrategy;
import org.apache.camel.support.resume.Resumables;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * File consumer.
 */
public class FileConsumer extends GenericFileConsumer<File> implements ResumeAware<ResumeStrategy> {

    private static final Logger LOG = LoggerFactory.getLogger(FileConsumer.class);
    private ResumeStrategy resumeStrategy;
    private String endpointPath;
    private Set<String> extendedAttributes;

    public FileConsumer(FileEndpoint endpoint, Processor processor, GenericFileOperations<File> operations,
                        GenericFileProcessStrategy<File> processStrategy) {
        super(endpoint, processor, operations, processStrategy);
        this.endpointPath = endpoint.getConfiguration().getDirectory();

        if (endpoint.getExtendedAttributes() != null) {
            List<String> attributes = Arrays.asList(endpoint.getExtendedAttributes().split(","));
            this.extendedAttributes = new HashSet<>(attributes);
        }
    }

    @Override
    protected Exchange createExchange(GenericFile<File> file) {
        Exchange exchange = createExchange(true);
        if (file != null) {
            file.bindToExchange(exchange, getEndpoint().isProbeContentType());
        }
        return exchange;
    }

    private boolean pollDirectory(Path directory, List<GenericFile<File>> fileList, final int depth) {
        try {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Polling directory: {}, absolute path: {}", directory, directory.toAbsolutePath());
            }

            // If the directory is empty, return
            if (isDirectoryEmpty(directory)) {
                return true;
            }

            final Polling polling = new Polling();

            try (Stream<Path> directoryStream = listFiles(directory)) {
                directoryStream.forEach(file -> {
                    // check if we can continue polling in files
                    if (!canPollMoreFiles(fileList)) {
                        polling.stop();
                    }

                    if (polling.canContinue()) {
                        // trace log as Windows/Unix can have different views what the file is
                        if (LOG.isTraceEnabled()) {
                            LOG.trace("Found file: {} [isAbsolute: {}, isDirectory: {}, isFile: {}, isHidden: {}]", file,
                                    file.isAbsolute(), Files.isDirectory(file), Files.isRegularFile(file),
                                    file.toFile().isHidden());
                        }

                        // creates a generic file
                        GenericFile<File> gf
                                = asGenericFile(endpointPath, file, getEndpoint().getCharset(),
                                        getEndpoint().isProbeContentType());

                        if (resumeStrategy != null) {
                            ResumeAdapter adapter = resumeStrategy.getAdapter();
                            if (adapter instanceof FileOffsetResumeAdapter) {
                                ((FileOffsetResumeAdapter) adapter).setResumePayload(gf);
                                adapter.resume();
                            }
                        }

                        if (Files.isDirectory(file)) {
                            if (endpoint.isRecursive() && depth + 1 < endpoint.getMaxDepth() && isValidDirectory(gf)) {
                                boolean canPollMore = pollDirectory(file, fileList, depth + 1);
                                if (!canPollMore) {
                                    polling.stop();
                                }
                            }
                        } else {
                            // Windows can report false to a file on a share so regard it
                            // always as a file (if it is not a directory)
                            if (depth + 1 >= endpoint.minDepth && isValidRegularFile(gf)) {
                                LOG.trace("Adding valid file: {}", file);
                                // matched file so add
                                if (extendedAttributes != null) {
                                    Map<String, Object> allAttributes = new HashMap<>();
                                    for (String attribute : extendedAttributes) {
                                        readAttributes(file, allAttributes, attribute);
                                    }
                                    gf.setExtendedAttributes(allAttributes);
                                }
                                fileList.add(gf);
                            }
                        }
                    }
                });
            }
            return polling.canContinue();
        } catch (IOException ex) {
            throw new GenericFileOperationFailedException("Polling of directory " + directory + " has failed", ex);
        }
    }

    @Override
    protected boolean pollDirectory(String fileName, List<GenericFile<File>> fileList, int depth) {
        LOG.trace("pollDirectory from fileName: {}", fileName);

        Path directory = Path.of(fileName);
        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            LOG.debug("Cannot poll as directory does not exists or its not a directory: {}", directory);
            if (getEndpoint().isDirectoryMustExist()) {
                throw new GenericFileOperationFailedException("Directory does not exist: " + directory);
            }
            return true;
        }

        return pollDirectory(directory, fileList, depth);
    }

    private Stream<Path> listFiles(Path directory) throws IOException {
        Stream<Path> fileStream = getEndpoint().isPreSort()
                ? Files.list(directory).sorted(Comparator.comparing(Path::toAbsolutePath))
                : Files.list(directory);

        if (isDirectoryEmpty(directory)) {
            // no files in this directory to poll
            if (LOG.isTraceEnabled()) {
                LOG.trace("No files found in directory: {}", directory);
            }
            return fileStream;
        } else {
            // we found some files
            if (LOG.isTraceEnabled()) {
                LOG.trace("Found files in directory: {}", directory);
            }
        }

        if (resumeStrategy != null) {
            ResumeAdapter adapter = resumeStrategy.getAdapter();
            if (adapter instanceof DirectoryEntriesResumeAdapter) {
                DirectoryEntries resumeSet = new DirectoryEntries(directory, fileStream);

                ((DirectoryEntriesResumeAdapter) adapter).setResumePayload(resumeSet);
                adapter.resume();

                return resumeSet.resumed();
            }
        }
        return fileStream;
    }

    private void readAttributes(Path path, Map<String, Object> allAttributes, String attribute) {
        try {
            String prefix = null;
            if (attribute.endsWith(":*")) {
                prefix = attribute.substring(0, attribute.length() - 1);
            } else if (attribute.equals("*")) {
                prefix = "basic:";
            }

            if (ObjectHelper.isNotEmpty(prefix)) {
                Map<String, Object> attributes = Files.readAttributes(path, attribute);
                if (attributes != null) {
                    for (Map.Entry<String, Object> entry : attributes.entrySet()) {
                        allAttributes.put(prefix + entry.getKey(), entry.getValue());
                    }
                }
            } else if (!attribute.contains(":")) {
                allAttributes.put("basic:" + attribute, Files.getAttribute(path, attribute));
            } else {
                allAttributes.put(attribute, Files.getAttribute(path, attribute));
            }
        } catch (IOException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Unable to read attribute {} on file {}", attribute, path, e);
            }
        }
    }

    @Override
    protected boolean isMatched(GenericFile<File> file, String doneFileName, File[] files) {
        Path doneFile = Path.of(doneFileName);
        if (Files.exists(doneFile)) {
            return true;
        } else {
            LOG.trace("Done file: {} does not exist", doneFile);
            return false;
        }
    }

    private static GenericFile<File> asGenericFile(String endpointPath, Path path, String charset, boolean probeContentType) {
        return asGenericFile(endpointPath, path.toFile(), charset, probeContentType);
    }

    /**
     * Creates a new GenericFile<File> based on the given file.
     *
     * @param  endpointPath     the starting directory the endpoint was configured with
     * @param  file             the source file
     * @param  probeContentType whether to probe the content type of the file or not
     * @return                  wrapped as a GenericFile
     */
    public static GenericFile<File> asGenericFile(String endpointPath, File file, String charset, boolean probeContentType) {
        GenericFile<File> answer = new GenericFile<>(probeContentType);
        // use file specific binding
        answer.setBinding(new FileBinding());

        answer.setCharset(charset);
        answer.setEndpointPath(endpointPath);
        answer.setFile(file);
        answer.setFileNameOnly(file.getName());
        answer.setFileLength(file.length());
        answer.setDirectory(file.isDirectory());
        // must use FileUtil.isAbsolute to have consistent check for whether the
        // file is
        // absolute or not. As windows do not consider \ paths as absolute where
        // as all
        // other OS platforms will consider \ as absolute. The logic in Camel
        // mandates
        // that we align this for all OS. That is why we must use
        // FileUtil.isAbsolute
        // to return a consistent answer for all OS platforms.
        answer.setAbsolute(FileUtil.isAbsolute(file));
        answer.setAbsoluteFilePath(file.getAbsolutePath());
        answer.setLastModified(file.lastModified());

        // compute the file path as relative to the starting directory
        File path;
        String endpointNormalizedSep = FileUtil.normalizePath(endpointPath) + File.separator;
        String p = file.getPath();
        if (p.startsWith(endpointNormalizedSep)) {
            p = p.substring(endpointNormalizedSep.length());
        }
        path = new File(p);

        if (path.getParent() != null) {
            answer.setRelativeFilePath(path.getParent() + File.separator + file.getName());
        } else {
            answer.setRelativeFilePath(path.getName());
        }

        // the file name should be the relative path
        answer.setFileName(answer.getRelativeFilePath());

        // use file as body as we have converters if needed as stream
        answer.setBody(file);
        return answer;
    }

    @Override
    protected void updateFileHeaders(GenericFile<File> file, Message message) {
        File upToDateFile = file.getFile();
        if (fileHasMoved(file)) {
            upToDateFile = new File(file.getAbsoluteFilePath());
        }
        long length = upToDateFile.length();
        long modified = upToDateFile.lastModified();
        file.setFileLength(length);
        file.setLastModified(modified);
        if (length >= 0) {
            message.setHeader(FileConstants.FILE_LENGTH, length);
        }
        if (modified >= 0) {
            message.setHeader(FileConstants.FILE_LAST_MODIFIED, modified);
        }

        message.setHeader(FileConstants.INITIAL_OFFSET, Resumables.of(upToDateFile, file.getLastOffsetValue()));
    }

    @Override
    public FileEndpoint getEndpoint() {
        return (FileEndpoint) super.getEndpoint();
    }

    private boolean fileHasMoved(GenericFile<File> file) {
        // GenericFile's absolute path is always up to date whereas the
        // underlying file is not
        Path expected = Path.of(file.getAbsoluteFilePath());
        return !file.getFile().toPath().toAbsolutePath().equals(expected);
    }

    @Override
    protected void doStart() throws Exception {
        if (resumeStrategy != null) {
            resumeStrategy.loadCache();
        }

        super.doStart();
    }

    @Override
    public ResumeStrategy getResumeStrategy() {
        return resumeStrategy;
    }

    @Override
    public void setResumeStrategy(ResumeStrategy resumeStrategy) {
        this.resumeStrategy = resumeStrategy;
    }

    private boolean isDirectoryEmpty(Path directory) throws IOException {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.findFirst().isEmpty();
        }
    }

    private boolean isValidDirectory(GenericFile<File> gf) {
        return isValidFile(gf, true, new File[0]);
    }

    private boolean isValidRegularFile(GenericFile<File> gf) {
        return isValidFile(gf, false, new File[0]);
    }

    private static class Polling {

        private boolean canPollMore = true;

        public void stop() {
            canPollMore = false;
        }

        public boolean canContinue() {
            return canPollMore;
        }

    }

}
