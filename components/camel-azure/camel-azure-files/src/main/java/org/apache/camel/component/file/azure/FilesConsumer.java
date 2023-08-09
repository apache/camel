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
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.file.GenericFileProcessStrategy;
import org.apache.camel.component.file.remote.RemoteFile;
import org.apache.camel.component.file.remote.RemoteFileConfiguration;
import org.apache.camel.component.file.remote.RemoteFileConsumer;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ManagedResource(description = "Camel Azure Files consumer")
public class FilesConsumer extends RemoteFileConsumer<ShareFileItem> {

    private static final Logger LOG = LoggerFactory.getLogger(FilesConsumer.class);

    protected String endpointPath;

    private transient String toString;

    public FilesConsumer(FilesEndpoint endpoint, Processor processor,
                         FilesOperations fileOperations,
                         GenericFileProcessStrategy<?> processStrategy) {
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
                LOG.debug("Auto creating directory: {}", endpointPath);
                try {
                    connectIfNecessary();
                    operations.buildDirectory(endpointPath, true);
                } catch (GenericFileOperationFailedException e) {
                    // log a WARN as we want to start the consumer.
                    LOG.warn("Error auto creating directory: {} due to {}. This exception is ignored.", endpointPath,
                            e.getMessage(),
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
    protected boolean pollDirectory(
            String path, List<GenericFile<ShareFileItem>> fileList,
            int depth) {
        LOG.trace("pollDirectory({},,{})", path, depth);

        return doPollDirectory(FilesPath.trimTrailingSeparator(path), null, fileList, depth);
    }

    @Override
    protected boolean doPollDirectory(
            String path, String dirName,
            List<GenericFile<ShareFileItem>> fileList, int depth) {
        LOG.trace("doPollDirectory({},{},,{})", path, dirName, depth);

        var listedFileItems = listFileItems(path);

        if (listedFileItems == null || listedFileItems.length == 0) {
            LOG.trace("No files found in directory: {}", path);
            return true;
        }

        LOG.trace("Found {} files in directory: {}", listedFileItems.length, path);

        if (getEndpoint().isPreSort()) {
            Arrays.sort(listedFileItems, Comparator.comparing(ShareFileItem::getName));
        }

        for (var fileItem : listedFileItems) {
            if (handleFileItem(path, fileList, depth + 1, listedFileItems, fileItem)) {
                return false;
            }
        }

        return true;
    }

    private boolean handleFileItem(
            String path, List<GenericFile<ShareFileItem>> polledFiles,
            int depth, ShareFileItem[] listedFileItems, ShareFileItem fileItem) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Item[name={}, dir={}]", fileItem.getName(), fileItem.isDirectory());
        }

        // check if we can continue polling in files
        if (!canPollMoreFiles(polledFiles)) {
            return true;
        }

        if (fileItem.isDirectory()) {
            if (handleDirectory(path, polledFiles, depth, listedFileItems, fileItem)) {
                return true;
            }
        } else {
            handleFile(path, polledFiles, depth, listedFileItems, fileItem);
        }
        return false;
    }

    private boolean handleDirectory(
            String path, List<GenericFile<ShareFileItem>> polledFiles,
            int depth, ShareFileItem[] listedFileItems, ShareFileItem dir) {

        if (endpoint.isRecursive() && depth < endpoint.getMaxDepth()) {
            var remote = asRemoteFile(path, dir);
            if (isValidFile(remote, true, listedFileItems)) {
                String dirName = dir.getName();
                String dirPath = FilesPath.concat(path, dirName);
                boolean canPollMore = doSafePollSubDirectory(dirPath, dirName, polledFiles, depth);
                if (!canPollMore) {
                    return true;
                }
            }
        }
        return false;
    }

    private void handleFile(
            String path, List<GenericFile<ShareFileItem>> polledFiles, int depth,
            ShareFileItem[] listedFileItems, ShareFileItem file) {
        if (depth >= endpoint.getMinDepth()) {
            var remote = asRemoteFile(path, file);
            if (isValidFile(remote, false, listedFileItems)) {
                polledFiles.add(remote);
            }
        }
    }

    private ShareFileItem[] listFileItems(String dir) {
        try {
            return operations.listFiles(dir);
        } catch (GenericFileOperationFailedException e) {
            if (ignoreCannotRetrieveFile(null, null, e)) {
                LOG.debug(
                        "Cannot list files in directory {} due directory does not exists or file permission error.",
                        dir);
                return null;
            } else {
                throw e;
            }
        }
    }

    @Override
    protected boolean isMatched(
            GenericFile<ShareFileItem> file, String doneFileName,
            ShareFileItem[] files) {
        String onlyName = FileUtil.stripPath(doneFileName);

        for (ShareFileItem f : files) {
            if (f.getName().equals(onlyName)) {
                return true;
            }
        }

        LOG.trace("Done file: {} does not exist", doneFileName);
        return false;
    }

    private RemoteFile<ShareFileItem> asRemoteFile(String path, ShareFileItem file) {
        RemoteFile<ShareFileItem> answer = new RemoteFile<>();

        answer.setEndpointPath(endpointPath);
        answer.setFile(file);
        answer.setFileNameOnly(file.getName());
        if (!file.isDirectory()) {
            answer.setFileLength(file.getFileSize());
            answer.setLastModified(lastModified(file));
        }
        answer.setDirectory(file.isDirectory());
        answer.setHostname(((RemoteFileConfiguration) endpoint.getConfiguration()).getHost());
        answer.setAbsolute(FilesPath.isAbsolute(path));

        String pseudoAbsoluteFileName = FilesPath.concat(path, file.getName());
        answer.setAbsoluteFilePath(pseudoAbsoluteFileName);

        String relativePath = StringHelper.after(pseudoAbsoluteFileName, endpointPath);
        relativePath = FilesPath.ensureRelative(relativePath);
        answer.setRelativeFilePath(relativePath);
        answer.setFileName(relativePath);

        return answer;
    }

    @Override
    protected void updateFileHeaders(GenericFile<ShareFileItem> file, Message message) {
        message.setHeader(FilesHeaders.FILE_LENGTH, file.getFileLength());
        message.setHeader(FilesHeaders.FILE_LAST_MODIFIED, file.getLastModified());
    }

    @Override
    public String toString() {
        if (toString == null) {
            toString = "FilesConsumer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return toString;
    }

    private static long lastModified(ShareFileItem file) {
        var raw = file.getProperties().getLastModified();
        if (raw == null) {
            // if ls without metadata
            return 0;
        }
        return raw.toInstant().toEpochMilli();
    }
}
