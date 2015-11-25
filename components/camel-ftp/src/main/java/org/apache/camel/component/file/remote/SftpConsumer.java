/**
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

import java.util.List;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpException;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;

/**
 * Secure FTP consumer
 */
public class SftpConsumer extends RemoteFileConsumer<ChannelSftp.LsEntry> {

    private String endpointPath;

    public SftpConsumer(RemoteFileEndpoint<ChannelSftp.LsEntry> endpoint, Processor processor, RemoteFileOperations<ChannelSftp.LsEntry> operations) {
        super(endpoint, processor, operations);
        this.endpointPath = endpoint.getConfiguration().getDirectory();
    }

    @Override
    protected void doStart() throws Exception {
        // turn off scheduler first, so autoCreate is handled before scheduler starts
        boolean startScheduler = isStartScheduler();
        setStartScheduler(false);
        try {
            super.doStart();
            if (endpoint.isAutoCreate()) {
                log.debug("Auto creating directory: {}", endpoint.getConfiguration().getDirectory());
                try {
                    connectIfNecessary();
                    operations.buildDirectory(endpoint.getConfiguration().getDirectory(), true);
                } catch (GenericFileOperationFailedException e) {
                    // log a WARN as we want to start the consumer.
                    log.warn("Error auto creating directory: " + endpoint.getConfiguration().getDirectory()
                            + " due " + e.getMessage() + ". This exception is ignored.", e);
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
    protected boolean pollDirectory(String fileName, List<GenericFile<ChannelSftp.LsEntry>> fileList, int depth) {
        String currentDir = null;
        if (isStepwise()) {
            // must remember current dir so we stay in that directory after the poll
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

    protected boolean pollSubDirectory(String absolutePath, String dirName, List<GenericFile<ChannelSftp.LsEntry>> fileList, int depth) {
        boolean answer = doSafePollSubDirectory(absolutePath, dirName, fileList, depth);
        // change back to parent directory when finished polling sub directory
        if (isStepwise()) {
            operations.changeToParentDirectory();
        }
        return answer;
    }

    protected boolean doPollDirectory(String absolutePath, String dirName, List<GenericFile<ChannelSftp.LsEntry>> fileList, int depth) {
        log.trace("doPollDirectory from absolutePath: {}, dirName: {}", absolutePath, dirName);

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

        log.trace("Polling directory: {}", dir);
        List<ChannelSftp.LsEntry> files;
        if (isStepwise()) {
            files = operations.listFiles();
        } else {
            files = operations.listFiles(dir);
        }
        if (files == null || files.isEmpty()) {
            // no files in this directory to poll
            log.trace("No files found in directory: {}", dir);
            return true;
        } else {
            // we found some files
            log.trace("Found {} in directory: {}", files.size(), dir);
        }

        for (ChannelSftp.LsEntry file : files) {

            if (log.isTraceEnabled()) {
                log.trace("SftpFile[fileName={}, longName={}, dir={}]", new Object[]{file.getFilename(), file.getLongname(), file.getAttrs().isDir()});
            }

            // check if we can continue polling in files
            if (!canPollMoreFiles(fileList)) {
                return false;
            }

            if (file.getAttrs().isDir()) {
                RemoteFile<ChannelSftp.LsEntry> remote = asRemoteFile(absolutePath, file, getEndpoint().getCharset());
                if (endpoint.isRecursive() && depth < endpoint.getMaxDepth() && isValidFile(remote, true, files)) {
                    // recursive scan and add the sub files and folders
                    String subDirectory = file.getFilename();
                    String path = absolutePath + "/" + subDirectory;
                    boolean canPollMore = pollSubDirectory(path, subDirectory, fileList, depth);
                    if (!canPollMore) {
                        return false;
                    }
                }
                // we cannot use file.getAttrs().isLink on Windows, so we dont invoke the method
                // just assuming its a file we should poll
            } else {
                RemoteFile<ChannelSftp.LsEntry> remote = asRemoteFile(absolutePath, file, getEndpoint().getCharset());
                if (depth >= endpoint.getMinDepth() && isValidFile(remote, false, files)) {
                    // matched file so add
                    fileList.add(remote);
                }
            }
        }

        return true;
    }

    @Override
    protected boolean isMatched(GenericFile<ChannelSftp.LsEntry> file, String doneFileName, List<ChannelSftp.LsEntry> files) {
        String onlyName = FileUtil.stripPath(doneFileName);

        for (ChannelSftp.LsEntry f : files) {
            if (f.getFilename().equals(onlyName)) {
                return true;
            }
        }

        log.trace("Done file: {} does not exist", doneFileName);
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

    private RemoteFile<ChannelSftp.LsEntry> asRemoteFile(String absolutePath, ChannelSftp.LsEntry file, String charset) {
        RemoteFile<ChannelSftp.LsEntry> answer = new RemoteFile<ChannelSftp.LsEntry>();

        answer.setCharset(charset);
        answer.setEndpointPath(endpointPath);
        answer.setFile(file);
        answer.setFileNameOnly(file.getFilename());
        answer.setFileLength(file.getAttrs().getSize());
        answer.setLastModified(file.getAttrs().getMTime() * 1000L);
        answer.setHostname(((RemoteFileConfiguration) endpoint.getConfiguration()).getHost());
        answer.setDirectory(file.getAttrs().isDir());

        // absolute or relative path
        boolean absolute = FileUtil.hasLeadingSeparator(absolutePath);
        answer.setAbsolute(absolute);

        // create a pseudo absolute name
        String dir = FileUtil.stripTrailingSeparator(absolutePath);
        String absoluteFileName = FileUtil.stripLeadingSeparator(dir + "/" + file.getFilename());
        // if absolute start with a leading separator otherwise let it be relative
        if (absolute) {
            absoluteFileName = "/" + absoluteFileName;
        }
        answer.setAbsoluteFilePath(absoluteFileName);

        // the relative filename, skip the leading endpoint configured path
        String relativePath = ObjectHelper.after(absoluteFileName, endpointPath);
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

}
