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

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import com.azure.storage.file.share.models.ShareFileItem;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.file.GenericFileProcessStrategy;
import org.apache.camel.component.file.remote.RemoteFile;
import org.apache.camel.component.file.remote.RemoteFileConfiguration;
import org.apache.camel.component.file.remote.RemoteFileConsumer;
import org.apache.camel.component.file.remote.RemoteFileEndpoint;
import org.apache.camel.component.file.remote.RemoteFileOperations;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ManagedResource(description = "Managed Azure Files Consumer")
public class FilesConsumer extends RemoteFileConsumer<ShareFileItem> {

    private static final Logger LOG = LoggerFactory.getLogger(FilesConsumer.class);

    protected String endpointPath;

    private transient String toString;

    public FilesConsumer(RemoteFileEndpoint<ShareFileItem> endpoint, Processor processor,
                         RemoteFileOperations<ShareFileItem> fileOperations,
                         GenericFileProcessStrategy processStrategy) {
        super(endpoint, processor, fileOperations, processStrategy);
        this.endpointPath = endpoint.getConfiguration().getDirectory();
    }

    @Override
    protected FilesOperations getOperations() {
        return (FilesOperations) super.getOperations();
    }

    @Override
    protected void doStart() throws Exception {
        // turn off scheduler first, so autoCreate is handled before scheduler
        // starts
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
    protected boolean pollDirectory(String fileName, List<GenericFile<ShareFileItem>> fileList, int depth) {
        String currentDir = null;
        // must remember current dir so we stay in that directory after the
        // poll
        currentDir = operations.getCurrentDirectory();

        // strip trailing slash
        fileName = FileUtil.stripTrailingSeparator(fileName);

        boolean answer = doPollDirectory(fileName, null, fileList, depth);
        if (currentDir != null) {
            operations.changeCurrentDirectory(currentDir);
        }

        return answer;
    }

    protected boolean pollSubDirectory(
            String absolutePath, String dirName, List<GenericFile<ShareFileItem>> fileList, int depth) {
        boolean answer = doSafePollSubDirectory(absolutePath, dirName, fileList, depth);
        // change back to parent directory when finished polling sub directory
        operations.changeToParentDirectory();
        return answer;
    }

    @Override
    protected boolean doPollDirectory(
            String absolutePath, String dirName, List<GenericFile<ShareFileItem>> fileList, int depth) {
        LOG.trace("doPollDirectory from absolutePath: {}, dirName: {}", absolutePath, dirName);

        depth++;

        // remove trailing /
        dirName = FileUtil.stripTrailingSeparator(dirName);

        // compute dir depending on stepwise is enabled or not
        final String dir = computeDir(absolutePath, dirName);

        final ShareFileItem[] files = listFiles(dir);

        if (files == null || files.length == 0) {
            // no files in this directory to poll
            LOG.trace("No files found in directory: {}", dir);
            return true;
        }

        // we found some files
        LOG.trace("Found {} files in directory: {}", files.length, dir);

        if (getEndpoint().isPreSort()) {
            Arrays.sort(files, Comparator.comparing(ShareFileItem::getName));
        }

        for (ShareFileItem file : files) {
            if (handleFiles(absolutePath, fileList, depth, files, file)) {
                return false;
            }
        }

        return true;
    }

    private boolean handleFiles(
            String absolutePath, List<GenericFile<ShareFileItem>> fileList, int depth, ShareFileItem[] files,
            ShareFileItem file) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Item[name={}, dir={}, file={}]", file.getName(), file.isDirectory(), !file.isDirectory());
        }

        // check if we can continue polling in files
        if (!canPollMoreFiles(fileList)) {
            return true;
        }

        if (file.isDirectory()) {
            if (handleDirectory(absolutePath, fileList, depth, files, file)) {
                return true;
            }
        } else {
            handleFile(absolutePath, fileList, depth, files, file);
        }
        return false;
    }

    private boolean handleDirectory(
            String absolutePath, List<GenericFile<ShareFileItem>> fileList, int depth, ShareFileItem[] files,
            ShareFileItem file) {
        RemoteFile<ShareFileItem> remote = asRemoteFile(absolutePath, file, getEndpoint().getCharset());
        if (endpoint.isRecursive() && depth < endpoint.getMaxDepth() && isValidFile(remote, true, files)) {
            // recursive scan and add the sub files and folders
            String subDirectory = file.getName();
            String path = ObjectHelper.isNotEmpty(absolutePath) ? absolutePath + "/" + subDirectory : subDirectory;
            boolean canPollMore = pollSubDirectory(path, subDirectory, fileList, depth);
            if (!canPollMore) {
                return true;
            }
        }
        return false;
    }

    private void handleFile(
            String absolutePath, List<GenericFile<ShareFileItem>> fileList, int depth, ShareFileItem[] files,
            ShareFileItem file) {
        RemoteFile<ShareFileItem> remote = asRemoteFile(absolutePath, file, getEndpoint().getCharset());
        if (depth >= endpoint.getMinDepth() && isValidFile(remote, false, files)) {
            // matched file so add
            fileList.add(remote);
        }
    }

    private String computeDir(String absolutePath, String dirName) {
        String dir;
        dir = ObjectHelper.isNotEmpty(dirName) ? dirName : absolutePath;
        operations.changeCurrentDirectory(dir);
        return dir;
    }

    private ShareFileItem[] listFilesImpl(String dir) {
        return operations.listFiles();
    }

    private ShareFileItem[] listFiles(String dir) {
        ShareFileItem[] files = null;
        try {
            LOG.trace("Polling directory: {}", dir);
            files = listFilesImpl(dir);
        } catch (GenericFileOperationFailedException e) {
            if (ignoreCannotRetrieveFile(null, null, e)) {
                LOG.debug("Cannot list files in directory {} due directory does not exists or file permission error.", dir);
            } else {
                throw e;
            }
        }
        return files;
    }

    @Override
    protected boolean isMatched(GenericFile<ShareFileItem> file, String doneFileName, ShareFileItem[] files) {
        String onlyName = FileUtil.stripPath(doneFileName);

        for (ShareFileItem f : files) {
            if (f.getName().equals(onlyName)) {
                return true;
            }
        }

        LOG.trace("Done file: {} does not exist", doneFileName);
        return false;
    }

    @Override
    protected boolean ignoreCannotRetrieveFile(String name, Exchange exchange, Exception cause) {
        if (getEndpoint().getConfiguration().isIgnoreFileNotFoundOrPermissionError()) {
            LOG.warn("The IgnoreFileNotFoundOrPermissionError option is not supported.");
        }
        return super.ignoreCannotRetrieveFile(name, exchange, cause);
    }

    private RemoteFile<ShareFileItem> asRemoteFile(String absolutePath, ShareFileItem file, String charset) {
        RemoteFile<ShareFileItem> answer = new RemoteFile<>();

        answer.setCharset(charset);
        answer.setEndpointPath(endpointPath);
        answer.setFile(file);
        answer.setFileNameOnly(file.getName());
        if (file.isDirectory() == false) {
            answer.setFileLength(file.getFileSize());
            answer.setLastModified(lastModified(file));
        }
        answer.setDirectory(file.isDirectory());
        answer.setHostname(((RemoteFileConfiguration) endpoint.getConfiguration()).getHost());

        // absolute or relative path
        boolean absolute = FileUtil.hasLeadingSeparator(absolutePath);
        answer.setAbsolute(absolute);

        // create a pseudo absolute name
        String dir = FileUtil.stripTrailingSeparator(absolutePath);
        String fileName = file.getName();
        String absoluteFileName = FileUtil.stripLeadingSeparator(dir + "/" + fileName);
        // if absolute start with a leading separator otherwise let it be
        // relative
        if (absolute) {
            absoluteFileName = "/" + absoluteFileName;
        }
        answer.setAbsoluteFilePath(absoluteFileName);

        // the relative filename, skip the leading endpoint configured path
        String relativePath = StringHelper.after(absoluteFileName, endpointPath);
        // skip leading /
        relativePath = FileUtil.stripLeadingSeparator(relativePath);
        answer.setRelativeFilePath(relativePath);

        // the file name should be the relative path
        answer.setFileName(answer.getRelativeFilePath());

        return answer;
    }

    @Override
    protected void updateFileHeaders(GenericFile<ShareFileItem> file, Message message) {
        long length = file.getFile().getFileSize();
        long modified = lastModified(file.getFile());
        file.setFileLength(length);
        file.setLastModified(modified);
        // TODO remove unnecessary conditions?
        if (length >= 0) {
            message.setHeader(FilesHeaders.FILE_LENGTH, length);
        }
        if (modified >= 0) {
            message.setHeader(FilesHeaders.FILE_LAST_MODIFIED, modified);
        }
    }

    @Override
    public String toString() {
        if (toString == null) {
            toString = "FilesConsumer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return toString;
    }

    private static long lastModified(ShareFileItem file) {
        var props = file.getProperties();
        // TODO properties are nulled, why?
        if (props.getLastModified() == null) {
            return -1;
        }
        return props.getLastModified().toInstant().toEpochMilli();
    }
}
