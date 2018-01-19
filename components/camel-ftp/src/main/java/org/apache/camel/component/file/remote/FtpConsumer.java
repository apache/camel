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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.URISupport;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;

/**
 * FTP consumer
 */
@ManagedResource(description = "Managed FtpConsumer")
public class FtpConsumer extends RemoteFileConsumer<FTPFile> {

    protected String endpointPath;
   
    private transient String ftpConsumerToString;

    public FtpConsumer(RemoteFileEndpoint<FTPFile> endpoint, Processor processor, RemoteFileOperations<FTPFile> fileOperations) {
        super(endpoint, processor, fileOperations);
        this.endpointPath = endpoint.getConfiguration().getDirectory();
    }

    @Override
    protected FtpOperations getOperations() {
        return (FtpOperations) super.getOperations();
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
    protected boolean pollDirectory(String fileName, List<GenericFile<FTPFile>> fileList, int depth) {
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

    protected boolean pollSubDirectory(String absolutePath, String dirName, List<GenericFile<FTPFile>> fileList, int depth) {
        boolean answer = doSafePollSubDirectory(absolutePath, dirName, fileList, depth);
        // change back to parent directory when finished polling sub directory
        if (isStepwise()) {
            operations.changeToParentDirectory();
        }
        return answer;
    }

    protected boolean doPollDirectory(String absolutePath, String dirName, List<GenericFile<FTPFile>> fileList, int depth) {
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
        List<FTPFile> files = null;
        if (isUseList()) {
            if (isStepwise()) {
                files = operations.listFiles();
            } else {
                files = operations.listFiles(dir);
            }
        } else {
            // we cannot use the LIST command(s) so we can only poll a named file
            // so created a pseudo file with that name
            FTPFile file = new FTPFile();
            file.setType(FTPFile.FILE_TYPE);
            fileExpressionResult = evaluateFileExpression();
            if (fileExpressionResult != null) {
                file.setName(fileExpressionResult);
                files = new ArrayList<FTPFile>(1);
                files.add(file);
            }
        }

        if (files == null || files.isEmpty()) {
            // no files in this directory to poll
            log.trace("No files found in directory: {}", dir);
            return true;
        } else {
            // we found some files
            log.trace("Found {} in directory: {}", files.size(), dir);
        }
        
        
        if (getEndpoint().isPreSort()) {
            Collections.sort(files, (a, b) -> a.getName().compareTo(b.getName()));
        }

        for (FTPFile file : files) {

            if (log.isTraceEnabled()) {
                log.trace("FtpFile[name={}, dir={}, file={}]", new Object[]{file.getName(), file.isDirectory(), file.isFile()});
            }

            // check if we can continue polling in files
            if (!canPollMoreFiles(fileList)) {
                return false;
            }

            if (file.isDirectory()) {
                RemoteFile<FTPFile> remote = asRemoteFile(absolutePath, file, getEndpoint().getCharset());
                if (endpoint.isRecursive() && depth < endpoint.getMaxDepth() && isValidFile(remote, true, files)) {
                    // recursive scan and add the sub files and folders
                    String subDirectory = file.getName();
                    String path = absolutePath + "/" + subDirectory;
                    boolean canPollMore = pollSubDirectory(path, subDirectory, fileList, depth);
                    if (!canPollMore) {
                        return false;
                    }
                }
            } else if (file.isFile()) {
                RemoteFile<FTPFile> remote = asRemoteFile(absolutePath, file, getEndpoint().getCharset());
                if (depth >= endpoint.getMinDepth() && isValidFile(remote, false, files)) {
                    // matched file so add
                    fileList.add(remote);
                }
            } else {
                log.debug("Ignoring unsupported remote file type: " + file);
            }
        }

        return true;
    }

    @Override
    protected boolean isMatched(GenericFile<FTPFile> file, String doneFileName, List<FTPFile> files) {
        String onlyName = FileUtil.stripPath(doneFileName);

        for (FTPFile f : files) {
            if (f.getName().equals(onlyName)) {
                return true;
            }
        }

        log.trace("Done file: {} does not exist", doneFileName);
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
                GenericFileOperationFailedException generic = ObjectHelper.getException(GenericFileOperationFailedException.class, cause);
                //exchange is null and cause has the reason for failure to read directories
                if (generic.getCode() == 550) {
                    return true;
                }
            }
        }
        return super.ignoreCannotRetrieveFile(name, exchange, cause);
    }

    private RemoteFile<FTPFile> asRemoteFile(String absolutePath, FTPFile file, String charset) {
        RemoteFile<FTPFile> answer = new RemoteFile<FTPFile>();

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
        if (((FtpConfiguration)endpoint.getConfiguration()).isHandleDirectoryParserAbsoluteResult()) {
            fileName = FtpUtils.extractDirNameFromAbsolutePath(file.getName());
        }
        String absoluteFileName =  FileUtil.stripLeadingSeparator(dir + "/" + fileName);
        // if absolute start with a leading separator otherwise let it be relative
        if (absolute) {
            absoluteFileName = "/" + absoluteFileName;
        }
        answer.setAbsoluteFilePath(absoluteFileName);

        // the relative filename, skip the leading endpoint configured path
        String relativePath = ObjectHelper.after(absoluteFileName, endpointPath);
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
            message.setHeader(Exchange.FILE_LENGTH, length);
        }
        if (modified >= 0) {
            message.setHeader(Exchange.FILE_LAST_MODIFIED, modified);
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
                    StopWatch watch = new StopWatch(new Date(since));
                    long delta = watch.taken();
                    String human = TimeUtils.printDuration(delta);
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
                    StopWatch watch = new StopWatch(new Date(since));
                    long delta = watch.taken();
                    String human = TimeUtils.printDuration(delta);
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
