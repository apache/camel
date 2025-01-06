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
package org.apache.camel.component.smb;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.protocol.commons.EnumWithValue;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileConsumer;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.GenericFileOperations;
import org.apache.camel.component.file.GenericFileProcessStrategy;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.function.Suppliers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SmbConsumer extends GenericFileConsumer<FileIdBothDirectoryInformation> {

    private static final Logger LOG = LoggerFactory.getLogger(SmbConsumer.class);

    protected String endpointPath;
    private final SmbEndpoint endpoint;
    private final SmbConfiguration configuration;

    public SmbConsumer(SmbEndpoint endpoint, Processor processor,
                       GenericFileOperations<FileIdBothDirectoryInformation> fileOperations,
                       GenericFileProcessStrategy<FileIdBothDirectoryInformation> processStrategy) {
        super(endpoint, processor, fileOperations, processStrategy);
        this.endpoint = endpoint;
        this.configuration = endpoint.getConfiguration();
        this.endpointPath = this.configuration.getDirectory() == null ? "" : this.configuration.getDirectory();
    }

    @Override
    @SuppressWarnings("unchecked")
    public GenericFileEndpoint<FileIdBothDirectoryInformation> getEndpoint() {
        return (GenericFileEndpoint<FileIdBothDirectoryInformation>) super.getEndpoint();
    }

    @Override
    protected boolean pollDirectory(String path, List<GenericFile<FileIdBothDirectoryInformation>> fileList, int depth) {
        depth++;
        path = (path == null) ? "" : path;
        FileIdBothDirectoryInformation[] files = getSmbFiles(path);

        if (files.length == 0) {
            LOG.trace("No files found in directory: {}", path);
            return true;
        }

        if (getEndpoint().isPreSort()) {
            Arrays.sort(files, Comparator.comparing(FileIdBothDirectoryInformation::getFileName));
        }

        for (FileIdBothDirectoryInformation file : files) {
            if (file.getFileName().equals(".") || file.getFileName().equals("..")) {
                continue;
            }
            if (!canPollMoreFiles(fileList)) {
                return false;
            }

            String fullFilePath = file.getFileName();
            if (!path.isEmpty()) {
                fullFilePath
                        = path + (path.endsWith("/") ? "" : "/") + file.getFileName();
            }

            if (handleSmbEntries(fullFilePath, fileList, depth, files, file)) {
                return false;
            }
        }
        return true;
    }

    private FileIdBothDirectoryInformation[] getSmbFiles(String dir) {
        LOG.trace("Polling directory: {}", dir);
        return getOperations().listFiles(dir, configuration.getSearchPattern());
    }

    private boolean handleSmbEntries(
            String fullFilePath, List<GenericFile<FileIdBothDirectoryInformation>> fileList, int depth,
            FileIdBothDirectoryInformation[] files, FileIdBothDirectoryInformation file) {

        if (isDirectory(file)) {
            LOG.trace("SmbFile[name={}, dir=true]", file.getFileName());
            return handleDirectory(fullFilePath, fileList, depth, files, file);
        } else {
            LOG.trace("SmbFile[name={}, file=true]", file.getFileName());
            handleFile(fullFilePath, fileList, depth, files, file);
        }
        return false;
    }

    private boolean handleDirectory(
            String fullFilePath, List<GenericFile<FileIdBothDirectoryInformation>> fileList, int depth,
            FileIdBothDirectoryInformation[] files, FileIdBothDirectoryInformation file) {

        SmbFile smbFile = asGenericFile(fullFilePath, file, getEndpoint().getCharset());
        Supplier<GenericFile<FileIdBothDirectoryInformation>> genericFileSupplier = Suppliers.memorize(() -> smbFile);
        Supplier<String> relativePath = smbFile::getRelativeFilePath;

        if (endpoint.isRecursive() && depth < endpoint.getMaxDepth() && isValidFile(genericFileSupplier, file.getFileName(),
                smbFile.getAbsoluteFilePath(), relativePath, true, files)) {

            // recursive scan and add the sub files and folders
            boolean canPollMore = pollDirectory(fullFilePath, fileList, depth);
            return !canPollMore;
        }
        return false;
    }

    private void handleFile(
            String fullFilePath, List<GenericFile<FileIdBothDirectoryInformation>> fileList, int depth,
            FileIdBothDirectoryInformation[] files, FileIdBothDirectoryInformation file) {

        SmbFile smbFile = asGenericFile(fullFilePath, file, getEndpoint().getCharset());
        Supplier<GenericFile<FileIdBothDirectoryInformation>> genericFileSupplier = Suppliers.memorize(() -> smbFile);
        Supplier<String> relativePath = smbFile::getRelativeFilePath;

        if (depth >= endpoint.getMinDepth() && isValidFile(genericFileSupplier, file.getFileName(),
                smbFile.getAbsoluteFilePath(), relativePath, false, files)) {

            fileList.add(smbFile);
        }
    }

    @Override
    protected Exchange createExchange(GenericFile<FileIdBothDirectoryInformation> file) {
        Exchange exchange = createExchange(true);
        if (file != null) {
            file.bindToExchange(exchange);
        }
        return exchange;
    }

    @Override
    protected void updateFileHeaders(GenericFile<FileIdBothDirectoryInformation> file, Message message) {
        // noop
    }

    @Override
    protected Supplier<String> getRelativeFilePath(
            String endpointPath, String path, String absolutePath, FileIdBothDirectoryInformation file) {
        return () -> {
            // the relative filename, skip the leading endpoint configured path
            String relativePath = StringHelper.after(absolutePath, endpointPath);
            // skip leading /
            return FileUtil.stripLeadingSeparator(relativePath);
        };
    }

    @Override
    protected boolean isMatched(
            Supplier<GenericFile<FileIdBothDirectoryInformation>> file, String doneFileName,
            FileIdBothDirectoryInformation[] files) {

        String onlyName = FileUtil.stripPath(doneFileName);
        for (FileIdBothDirectoryInformation f : files) {
            if (f.getFileName().equals(onlyName)) {
                return true;
            }
        }
        LOG.trace("Done file: {} does not exist", doneFileName);
        return false;
    }

    private SmbFile asGenericFile(String path, FileIdBothDirectoryInformation file, String charset) {
        SmbFile genericFile = new SmbFile(getOperations(), configuration.isStreamDownload());
        genericFile.setHostname(configuration.getHostname());
        genericFile.setFile(file);
        genericFile.setEndpointPath(endpointPath);
        genericFile.setLastModified(file.getChangeTime().toEpochMillis());
        genericFile.setCharset(charset);
        genericFile.setFileNameOnly(file.getFileName());
        genericFile.setDirectory(isDirectory(file));
        genericFile.setFileLength(file.getEndOfFile());

        boolean absolute = FileUtil.hasLeadingSeparator(path);
        genericFile.setAbsolute(absolute);

        String absoluteFileName = FileUtil.stripLeadingSeparator(path);
        // if absolute start with a leading separator otherwise let it be relative
        if (absolute) {
            absoluteFileName = "/" + absoluteFileName;
        }
        genericFile.setAbsoluteFilePath(absoluteFileName);

        String relativePath = StringHelper.after(absoluteFileName, endpointPath);
        relativePath = FileUtil.stripLeadingSeparator(relativePath);
        genericFile.setRelativeFilePath(relativePath);
        genericFile.setFileName(relativePath);

        return genericFile;
    }

    @Override
    protected void doStart() throws Exception {
        boolean startScheduler = isStartScheduler();
        setStartScheduler(false);
        try {
            super.doStart();
        } finally {
            if (startScheduler) {
                setStartScheduler(true);
                startScheduler();
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        getOperations().disconnect();
    }

    private SmbOperations getOperations() {
        return (SmbOperations) operations;
    }

    private boolean isDirectory(FileIdBothDirectoryInformation file) {
        return EnumWithValue.EnumUtils.isSet(file.getFileAttributes(), FileAttributes.FILE_ATTRIBUTE_DIRECTORY);
    }
}
