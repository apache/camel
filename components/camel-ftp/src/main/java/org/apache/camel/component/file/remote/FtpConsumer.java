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

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.file.GenericFileProcessStrategy;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.URISupport;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * FTP consumer
 */
@ManagedResource(description = "Managed FtpConsumer")
public class FtpConsumer extends RemoteFileConsumer<FTPFile> {

    private static final Logger LOG = LoggerFactory.getLogger(FtpConsumer.class);

    protected String endpointPath;

    private transient String ftpConsumerToString;

    public FtpConsumer(RemoteFileEndpoint<FTPFile> endpoint, Processor processor, RemoteFileOperations<FTPFile> fileOperations,
                       GenericFileProcessStrategy processStrategy) {
        super(endpoint, processor, fileOperations, processStrategy);
        this.endpointPath = endpoint.getConfiguration().getDirectory();
    }

    @Override
    protected FtpOperations getOperations() {
        return (FtpOperations) super.getOperations();
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
    protected boolean pollDirectory(String fileName, List<GenericFile<FTPFile>> fileList, int depth) {
        String currentDir = null;
        if (isStepwise()) {
            // must remember current dir so we stay in that directory after the
            // poll
            currentDir = operations.getCurrentDirectory();
        }

        // strip trailing slash
        fileName = FileUtil.stripTrailingSeparator(fileName);

        boolean answer = doPollDirectory(fileName, null, fileList, depth);
        if (currentDir != null) {
            operations.changeCurrentDirectory(currentDir);
        }

        return answer;
    }

    protected boolean pollSubDirectory(String absolutePath, String dirName, List<GenericFile<FTPFile>> fileList, int depth) {
        boolean answer = doSafePollSubDirectory(absolutePath, dirName, fileList, depth);
        // change back to parent directory when finished polling sub directory
        if (isStepwise()) {
            operations.changeToParentDirectory();
        }
        return answer;
    }

    @Override
    protected boolean doPollDirectory(String absolutePath, String dirName, List<GenericFile<FTPFile>> fileList, int depth) {
        LOG.trace("doPollDirectory from absolutePath: {}, dirName: {}", absolutePath, dirName);

        depth++;

        // remove trailing /
        dirName = FileUtil.stripTrailingSeparator(dirName);

        // compute dir depending on stepwise is enabled or not
        final String dir = computeDir(absolutePath, dirName);

        final FTPFile[] files = getFtpFiles(dir);

        if (files == null || files.length == 0) {
            // no files in this directory to poll
            LOG.trace("No files found in directory: {}", dir);
            return true;
        }

        // we found some files
        LOG.trace("Found {} files in directory: {}", files.length, dir);

        if (getEndpoint().isPreSort()) {
            Arrays.sort(files, Comparator.comparing(FTPFile::getName));
        }

        for (FTPFile file : files) {
            if (handleFtpEntries(absolutePath, fileList, depth, files, file)) {
                return false;
            }
        }

        return true;
    }

    private boolean handleFtpEntries(
            String absolutePath, List<GenericFile<FTPFile>> fileList, int depth, FTPFile[] files, FTPFile file) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("FtpFile[name={}, dir={}, file={}]", file.getName(), file.isDirectory(), file.isFile());
        }

        // check if we can continue polling in files
        if (!canPollMoreFiles(fileList)) {
            return true;
        }

        if (file.isDirectory()) {
            if (handleDirectory(absolutePath, fileList, depth, files, file)) {
                return true;
            }
        } else if (file.isFile()) {
            handleFile(absolutePath, fileList, depth, files, file);
        } else {
            LOG.debug("Ignoring unsupported remote file type: {}", file);
        }
        return false;
    }

    private boolean handleDirectory(
            String absolutePath, List<GenericFile<FTPFile>> fileList, int depth, FTPFile[] files, FTPFile file) {
        RemoteFile<FTPFile> remote = asRemoteFile(absolutePath, file, getEndpoint().getCharset());
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
            String absolutePath, List<GenericFile<FTPFile>> fileList, int depth, FTPFile[] files, FTPFile file) {
        RemoteFile<FTPFile> remote = asRemoteFile(absolutePath, file, getEndpoint().getCharset());
        if (depth >= endpoint.getMinDepth() && isValidFile(remote, false, files)) {
            // matched file so add
            fileList.add(remote);
        }
    }

    private String computeDir(String absolutePath, String dirName) {
        String dir;
        if (isStepwise()) {
            dir = ObjectHelper.isNotEmpty(dirName) ? dirName : absolutePath;
            operations.changeCurrentDirectory(dir);
        } else {
            dir = absolutePath;
        }
        return dir;
    }

    private FTPFile[] pollNamedFile() {
        FTPFile[] files = null;
        // we cannot use the LIST command(s) so we can only poll a named
        // file so created a pseudo file with that name
        Exchange dummy = endpoint.createExchange();
        String name = evaluateFileExpression(dummy);
        if (name != null) {
            FTPFile file = new FTPFile();
            file.setType(FTPFile.FILE_TYPE);
            file.setName(name);
            files = new FTPFile[1];
            files[0] = file;
        }
        return files;
    }

    private FTPFile[] listFiles(String dir) {
        if (isStepwise()) {
            return operations.listFiles();
        }

        return operations.listFiles(dir);
    }

    private FTPFile[] getFtpFiles(String dir) {
        FTPFile[] files = null;
        try {
            LOG.trace("Polling directory: {}", dir);
            if (isUseList()) {
                files = listFiles(dir);
            } else {
                files = pollNamedFile();
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

    @Override
    protected boolean isMatched(GenericFile<FTPFile> file, String doneFileName, FTPFile[] files) {
        String onlyName = FileUtil.stripPath(doneFileName);

        for (FTPFile f : files) {
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
            if (exchange != null) {
                // error code 550 is file not found
                int code = exchange.getIn().getHeader(FtpConstants.FTP_REPLY_CODE, 0, int.class);
                if (code == 550) {
                    return true;
                }
            }
            if (cause instanceof GenericFileOperationFailedException) {
                GenericFileOperationFailedException generic
                        = ObjectHelper.getException(GenericFileOperationFailedException.class, cause);
                // exchange is null and cause has the reason for failure to read
                // directories
                if (generic.getCode() == 550) {
                    return true;
                }
            }
        }
        return super.ignoreCannotRetrieveFile(name, exchange, cause);
    }

    private RemoteFile<FTPFile> asRemoteFile(String absolutePath, FTPFile file, String charset) {
        RemoteFile<FTPFile> answer = new RemoteFile<>();

        answer.setCharset(charset);
        answer.setEndpointPath(endpointPath);
        answer.setFile(file);
        answer.setFileNameOnly(file.getName());
        answer.setFileLength(file.getSize());
        answer.setDirectory(file.isDirectory());
        if (file.getTimestamp() != null) {
            answer.setLastModified(file.getTimestamp().getTimeInMillis());
        }
        answer.setHostname(((RemoteFileConfiguration) endpoint.getConfiguration()).getHost());

        // absolute or relative path
        boolean absolute = FileUtil.hasLeadingSeparator(absolutePath);
        answer.setAbsolute(absolute);

        // create a pseudo absolute name
        String dir = FileUtil.stripTrailingSeparator(absolutePath);
        String fileName = file.getName();
        if (((FtpConfiguration) endpoint.getConfiguration()).isHandleDirectoryParserAbsoluteResult()) {
            fileName = FtpUtils.extractDirNameFromAbsolutePath(file.getName());
        }
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
    protected void updateFileHeaders(GenericFile<FTPFile> file, Message message) {
        long length = file.getFile().getSize();
        long modified = file.getFile().getTimestamp() != null ? file.getFile().getTimestamp().getTimeInMillis() : -1;
        file.setFileLength(length);
        file.setLastModified(modified);
        if (length >= 0) {
            message.setHeader(FtpConstants.FILE_LENGTH, length);
        }
        if (modified >= 0) {
            message.setHeader(FtpConstants.FILE_LAST_MODIFIED, modified);
        }
    }

    private boolean isStepwise() {
        RemoteFileConfiguration config = (RemoteFileConfiguration) endpoint.getConfiguration();
        return config.isStepwise();
    }

    private boolean isUseList() {
        RemoteFileConfiguration config = (RemoteFileConfiguration) endpoint.getConfiguration();
        return config.isUseList();
    }

    @ManagedAttribute(description = "Summary of last FTP activity (download only)")
    public String getLastFtpActivity() {
        FTPClient client = getOperations().getFtpClient();
        FtpClientActivityListener listener = (FtpClientActivityListener) client.getCopyStreamListener();
        if (listener != null) {
            String log = listener.getLastLogActivity();
            if (log != null) {
                long since = listener.getLastLogActivityTimestamp();
                if (since > 0) {
                    String human = TimeUtils.printSince(since);
                    return log + " " + human + " ago";
                } else {
                    return log;
                }
            }
        }
        return null;
    }

    @ManagedAttribute(description = "Summary of last FTP activity (all)")
    public String getLastFtpActivityVerbose() {
        FTPClient client = getOperations().getFtpClient();
        FtpClientActivityListener listener = (FtpClientActivityListener) client.getCopyStreamListener();
        if (listener != null) {
            String log = listener.getLastVerboseLogActivity();
            if (log != null) {
                long since = listener.getLastVerboseLogActivityTimestamp();
                if (since > 0) {
                    String human = TimeUtils.printSince(since);
                    return log + " " + human + " ago";
                } else {
                    return log;
                }
            }
        }
        return null;
    }

    @Override
    public String toString() {
        if (ftpConsumerToString == null) {
            ftpConsumerToString = "FtpConsumer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return ftpConsumerToString;
    }
}
