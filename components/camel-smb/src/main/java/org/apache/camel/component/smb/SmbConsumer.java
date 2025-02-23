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
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Message;
import org.apache.camel.Ordered;
import org.apache.camel.Processor;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileConsumer;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.file.GenericFileOperations;
import org.apache.camel.component.file.GenericFileProcessStrategy;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.function.Suppliers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SmbConsumer extends GenericFileConsumer<FileIdBothDirectoryInformation> {

    private static final Logger LOG = LoggerFactory.getLogger(SmbConsumer.class);

    private final SmbEndpoint endpoint;
    private final SmbConfiguration configuration;
    private final String endpointPath;
    protected transient boolean loggedIn;
    protected transient boolean loggedInWarning;

    public SmbConsumer(SmbEndpoint endpoint, Processor processor,
                       GenericFileOperations<FileIdBothDirectoryInformation> fileOperations,
                       GenericFileProcessStrategy<FileIdBothDirectoryInformation> processStrategy) {
        super(endpoint, processor, fileOperations, processStrategy);
        this.endpoint = endpoint;
        this.configuration = endpoint.getConfiguration();
        this.endpointPath = this.configuration.getDirectory() == null ? "" : this.configuration.getDirectory();
        this.setRetrieveFile(configuration.isDownload());
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

        if (endpoint.isRecursive() && depth < endpoint.getMaxDepth()) {
            SmbFile smbFile = asGenericFile(fullFilePath, file, getEndpoint().getCharset());
            Supplier<GenericFile<FileIdBothDirectoryInformation>> genericFileSupplier = Suppliers.memorize(() -> smbFile);
            Supplier<String> relativePath = smbFile::getRelativeFilePath;
            if (isValidFile(genericFileSupplier, file.getFileName(),
                    smbFile.getAbsoluteFilePath(), relativePath, true, files)) {
                // recursive scan and add the sub files and folders
                boolean canPollMore = pollDirectory(fullFilePath, fileList, depth);
                return !canPollMore;
            }
        }
        return false;
    }

    private void handleFile(
            String fullFilePath, List<GenericFile<FileIdBothDirectoryInformation>> fileList, int depth,
            FileIdBothDirectoryInformation[] files, FileIdBothDirectoryInformation file) {

        if (depth >= endpoint.getMinDepth()) {
            SmbFile smbFile = asGenericFile(fullFilePath, file, getEndpoint().getCharset());
            Supplier<GenericFile<FileIdBothDirectoryInformation>> genericFileSupplier = Suppliers.memorize(() -> smbFile);
            Supplier<String> relativePath = smbFile::getRelativeFilePath;

            if (isValidFile(genericFileSupplier, file.getFileName(),
                    smbFile.getAbsoluteFilePath(), relativePath, false, files)) {
                fileList.add(smbFile);
            }
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

    @Override
    protected boolean prePollCheck() throws Exception {
        if (LOG.isTraceEnabled()) {
            LOG.trace("prePollCheck on {}", getEndpoint());
        }
        try {
            getOperations().connectIfNecessary();
            loggedIn = true;
        } catch (Exception e) {
            loggedIn = false;

            // login failed should we thrown exception
            if (configuration.isThrowExceptionOnConnectFailed()) {
                throw e;
            }
        }

        if (!loggedIn) {
            String message = "Cannot connect/login to: " + remoteServer() + ". Will skip this poll.";
            if (!loggedInWarning) {
                LOG.warn(message);
                loggedInWarning = true;
            }
            return false;
        } else {
            // need to log the failed log again
            loggedInWarning = false;
        }

        // we are logged in so lets mark the consumer as ready
        forceConsumerAsReady();

        return true;
    }

    /**
     * Returns human-readable server information for logging purpose
     */
    protected String remoteServer() {
        return configuration.remoteServerInformation();
    }

    protected void disconnect() {
        // eager indicate we are no longer logged in
        loggedIn = false;

        // disconnect
        try {
            if (getOperations().isConnected()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Disconnecting from: {}", remoteServer());
                }
                getOperations().disconnect();
            }
        } catch (Exception e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Error occurred while disconnecting from {} due: {} This exception will be ignored.",
                        remoteServer(), e.getMessage(), e);
            }
        }
    }

    @Override
    protected void postPollCheck(int polledMessages) {
        // if we did not poll any messages, but are configured to disconnect
        // then we need to do this now
        // as there is no exchanges to be routed that otherwise will disconnect
        // from the last UoW
        if (polledMessages == 0) {
            if (configuration.isDisconnect()) {
                LOG.trace("postPollCheck disconnect from: {}", getEndpoint());
                disconnect();
            }
        }
    }

    @Override
    protected boolean processExchange(Exchange exchange) {
        // defer disconnect til the UoW is complete - but only the last exchange
        // from the batch should do that
        boolean isLast = exchange.getProperty(ExchangePropertyKey.BATCH_COMPLETE, true, Boolean.class);
        if (isLast && configuration.isDisconnect()) {
            exchange.getExchangeExtension().addOnCompletion(new SynchronizationAdapter() {
                @Override
                public void onDone(Exchange exchange) {
                    LOG.trace("processExchange disconnect from: {}", getEndpoint());
                    disconnect();
                }

                @Override
                public boolean allowHandover() {
                    // do not allow handover as we must execute the callbacks in
                    // the same thread as this consumer
                    return false;
                }

                @Override
                public int getOrder() {
                    // we want to disconnect last
                    return Ordered.LOWEST;
                }

                public String toString() {
                    return "Disconnect";
                }
            });
        }

        return super.processExchange(exchange);
    }

    private SmbFile asGenericFile(String path, FileIdBothDirectoryInformation file, String charset) {
        SmbFile genericFile = new SmbFile(getOperations(), configuration.isDownload(), configuration.isStreamDownload());
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
        // turn off scheduler first, so autoCreate is handled before scheduler starts
        boolean startScheduler = isStartScheduler();
        setStartScheduler(false);
        try {
            super.doStart();
            if (endpoint.isAutoCreate() && hasStartingDirectory()) {
                String dir = endpoint.getConfiguration().getDirectory();
                LOG.debug("Auto creating directory: {}", dir);
                try {
                    operations.buildDirectory(dir, true);
                } catch (GenericFileOperationFailedException e) {
                    // log a WARN as we want to start the consumer.
                    LOG.warn(
                            "Error auto creating directory: " + dir + " due " + e.getMessage() + ". This exception is ignored.",
                            e);
                }
            } else if (configuration.isStartingDirectoryMustExist() && hasStartingDirectory()) {
                String dir = endpoint.getConfiguration().getDirectory();
                SmbOperations ops = (SmbOperations) operations;
                boolean exists = ops.existsFolder(dir);
                if (!exists) {
                    throw new GenericFileOperationFailedException("Starting directory does not exist: " + dir);
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
    protected void doStop() throws Exception {
        super.doStop();
        disconnect();
    }

    private SmbOperations getOperations() {
        return (SmbOperations) operations;
    }

    /**
     * Whether there is a starting directory configured.
     */
    private boolean hasStartingDirectory() {
        String dir = endpoint.getConfiguration().getDirectory();
        if (ObjectHelper.isEmpty(dir)) {
            return false;
        }
        // should not be an empty separator
        return !dir.equals("/") && !dir.equals("\\");
    }

    private boolean isDirectory(FileIdBothDirectoryInformation file) {
        return EnumWithValue.EnumUtils.isSet(file.getFileAttributes(), FileAttributes.FILE_ATTRIBUTE_DIRECTORY);
    }
}
