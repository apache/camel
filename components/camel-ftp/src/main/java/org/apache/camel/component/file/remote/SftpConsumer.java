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

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpException;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.file.GenericFileProcessStrategy;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Secure FTP consumer
 */
public class SftpConsumer extends RemoteFileConsumer<SftpRemoteFile> {

    private static final Logger LOG = LoggerFactory.getLogger(SftpConsumer.class);

    private String endpointPath;

    private transient String sftpConsumerToString;

    public SftpConsumer(RemoteFileEndpoint<SftpRemoteFile> endpoint, Processor processor,
                        RemoteFileOperations<SftpRemoteFile> operations,
                        GenericFileProcessStrategy<SftpRemoteFile> processStrategy) {
        super(endpoint, processor, operations, processStrategy);
        this.endpointPath = endpoint.getConfiguration().getDirectory();
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
    protected boolean pollDirectory(String fileName, List<GenericFile<SftpRemoteFile>> fileList, int depth) {
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

    protected boolean pollSubDirectory(
            String absolutePath, String dirName, List<GenericFile<SftpRemoteFile>> fileList, int depth) {
        boolean answer = doSafePollSubDirectory(absolutePath, dirName, fileList, depth);
        // change back to parent directory when finished polling sub directory
        if (isStepwise()) {
            operations.changeToParentDirectory();
        }
        return answer;
    }

    @Override
    protected boolean doPollDirectory(
            String absolutePath, String dirName, List<GenericFile<SftpRemoteFile>> fileList, int depth) {
        LOG.trace("doPollDirectory from absolutePath: {}, dirName: {}", absolutePath, dirName);

        depth++;

        // remove trailing /
        dirName = FileUtil.stripTrailingSeparator(dirName);

        // compute dir depending on stepwise is enabled or not
        String dir = null;
        if (isStepwise()) {
            dir = ObjectHelper.isNotEmpty(dirName) ? dirName : absolutePath;
            operations.changeCurrentDirectory(dir);
        } else {
            dir = absolutePath;
        }

        final SftpRemoteFile[] files = getSftpRemoteFiles(dir);

        if (files == null || files.length == 0) {
            // no files in this directory to poll
            LOG.trace("No files found in directory: {}", dir);
            return true;
        } else {
            // we found some files
            LOG.trace("Found {} files in directory: {}", files.length, dir);
        }

        if (getEndpoint().isPreSort()) {
            Arrays.sort(files, Comparator.comparing(SftpRemoteFile::getFilename));
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
                RemoteFile<SftpRemoteFile> remote = asRemoteFile(absolutePath, file, getEndpoint().getCharset());
                if (endpoint.isRecursive() && depth < endpoint.getMaxDepth() && isValidFile(remote, true, files)) {
                    // recursive scan and add the sub files and folders
                    String subDirectory = file.getFilename();
                    String path = ObjectHelper.isNotEmpty(absolutePath) ? absolutePath + "/" + subDirectory : subDirectory;
                    boolean canPollMore = pollSubDirectory(path, subDirectory, fileList, depth);
                    if (!canPollMore) {
                        return false;
                    }
                }
                // we cannot use file.getAttrs().isLink on Windows, so we dont
                // invoke the method
                // just assuming its a file we should poll
            } else {
                RemoteFile<SftpRemoteFile> remote = asRemoteFile(absolutePath, file, getEndpoint().getCharset());
                if (depth >= endpoint.getMinDepth() && isValidFile(remote, false, files)) {
                    // matched file so add
                    fileList.add(remote);
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

    private SftpRemoteFile[] getSftpRemoteFiles(String dir) {
        SftpRemoteFile[] files = null;
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

    private SftpRemoteFile[] pollNamedFile() {
        SftpRemoteFile[] files = null;

        // we cannot use the LIST command(s) so we can only poll a named
        // file so created a pseudo file with that name
        Exchange dummy = endpoint.createExchange();
        String name = evaluateFileExpression(dummy);
        if (name != null) {
            SftpRemoteFile file = new SftpRemoteFileSingle(name);
            files = new SftpRemoteFile[1];
            files[0] = file;
        }

        return files;
    }

    @Override
    protected boolean isMatched(GenericFile<SftpRemoteFile> file, String doneFileName, SftpRemoteFile[] files) {
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
    protected boolean ignoreCannotRetrieveFile(String name, Exchange exchange, Exception cause) {
        if (getEndpoint().getConfiguration().isIgnoreFileNotFoundOrPermissionError()) {
            SftpException sftp = ObjectHelper.getException(SftpException.class, cause);
            if (sftp != null) {
                return sftp.id == ChannelSftp.SSH_FX_NO_SUCH_FILE || sftp.id == ChannelSftp.SSH_FX_PERMISSION_DENIED;
            }
        }
        return super.ignoreCannotRetrieveFile(name, exchange, cause);
    }

    private RemoteFile<SftpRemoteFile> asRemoteFile(String absolutePath, SftpRemoteFile file, String charset) {
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

        // create a pseudo absolute name
        String dir = FileUtil.stripTrailingSeparator(absolutePath);
        String absoluteFileName = FileUtil.stripLeadingSeparator(dir + "/" + file.getFilename());
        // if absolute start with a leading separator otherwise let it be
        // relative
        if (absolute) {
            absoluteFileName = "/" + absoluteFileName;
        }
        answer.setAbsoluteFilePath(absoluteFileName);

        // the relative filename, skip the leading endpoint configured path
        String relativePath = StringHelper.after(absoluteFileName, endpointPath);
        // skip trailing /
        relativePath = FileUtil.stripLeadingSeparator(relativePath);
        answer.setRelativeFilePath(relativePath);

        // the file name should be the relative path
        answer.setFileName(answer.getRelativeFilePath());

        return answer;
    }

    @Override
    protected void updateFileHeaders(GenericFile<SftpRemoteFile> file, Message message) {
        Object rf = file.getFile().getRemoteFile();
        if (rf != null) {
            ChannelSftp.LsEntry e = (ChannelSftp.LsEntry) rf;
            long length = e.getAttrs().getSize();
            long modified = e.getAttrs().getMTime() * 1000L;
            file.setFileLength(length);
            file.setLastModified(modified);
            if (length >= 0) {
                message.setHeader(FtpConstants.FILE_LENGTH, length);
            }
            if (modified >= 0) {
                message.setHeader(FtpConstants.FILE_LAST_MODIFIED, modified);
            }
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

    @Override
    public String toString() {
        if (sftpConsumerToString == null) {
            sftpConsumerToString = "SftpConsumer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return sftpConsumerToString;
    }

}
