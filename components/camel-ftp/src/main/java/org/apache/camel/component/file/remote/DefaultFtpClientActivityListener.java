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

import org.apache.camel.spi.CamelLogger;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.commons.net.io.CopyStreamEvent;
import org.apache.commons.net.io.CopyStreamListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultFtpClientActivityListener implements FtpClientActivityListener, CopyStreamListener {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultFtpClientActivityListener.class);

    private final CamelLogger logger;
    private final String host;
    private final FtpEndpoint endpoint;
    private boolean download = true;
    private boolean resume;
    private long resumeOffset;

    private String fileName;
    private long fileSize;
    private String fileSizeText;
    private String lastLogActivity;
    private String lastVerboseLogActivity;
    private long lastLogActivityTimestamp = -1;
    private long lastVerboseLogActivityTimestamp = -1;
    private long transferredBytes;
    private final StopWatch watch = new StopWatch();
    private final StopWatch interval = new StopWatch();

    public DefaultFtpClientActivityListener(FtpEndpoint endpoint, String host) {
        this.logger = new CamelLogger(LOG);
        this.endpoint = endpoint;
        this.host = host;
    }

    @Override
    public void setDownload(boolean download) {
        this.download = download;
    }

    @Override
    public void setRemoteFileName(String fileName) {
        this.fileName = fileName;
    }

    @Override
    public void setRemoteFileSize(long fileSize) {
        this.fileSize = fileSize;
        this.fileSizeText = StringHelper.humanReadableBytes(fileSize);
    }

    @Override
    public String getLastLogActivity() {
        return lastLogActivity;
    }

    @Override
    public long getLastLogActivityTimestamp() {
        return lastLogActivityTimestamp;
    }

    @Override
    public String getLastVerboseLogActivity() {
        return lastVerboseLogActivity;
    }

    @Override
    public long getLastVerboseLogActivityTimestamp() {
        return lastVerboseLogActivityTimestamp;
    }

    @Override
    public void onGeneralError(String host, String errorMessage) {
        doLogVerbose("General error when communicating with host: " + host + " error: " + errorMessage);
    }

    @Override
    public void onConnecting(String host) {
        doLogVerbose("Connecting to host: " + host);
    }

    @Override
    public void onConnected(String host) {
        doLogVerbose("Connected to host: " + host);
    }

    @Override
    public void onLogin(String host) {
        doLogVerbose("Login on host: " + host);
    }

    @Override
    public void onLoginComplete(String host) {
        doLogVerbose("Login on host: " + host + " complete");
    }

    @Override
    public void onLoginFailed(int replyCode, String replyMessage) {
        doLogVerbose("Login on host: " + host + " failed (code: " + replyCode + ", message: " + replyMessage + ")");
    }

    @Override
    public void onDisconnecting(String host) {
        doLogVerbose("Disconnecting from host: " + host);
    }

    @Override
    public void onDisconnected(String host) {
        doLogVerbose("Disconnected from host: " + host);
    }

    @Override
    public void onScanningForFiles(String host, String directory) {
        if (ObjectHelper.isEmpty(directory)) {
            doLogVerbose("Scanning for new files to download from host: " + host);
        } else {
            doLogVerbose("Scanning for new files to download from host: " + host + " in directory: " + directory);
        }
    }

    @Override
    public void onBeginDownloading(String host, String file) {
        download = true;
        resume = false;
        resumeOffset = 0;
        watch.restart();
        interval.restart();
        String msg = "Downloading from host: " + host + " file: " + file + " starting "; // add
                                                                                        // extra
                                                                                        // space
                                                                                        // to
                                                                                        // align
                                                                                        // with
                                                                                        // completed
        if (fileSize > 0) {
            msg += " (size: " + fileSizeText + ")";
        }
        doLog(msg);
    }

    @Override
    public void onResumeDownloading(String host, String file, long position) {
        download = true;
        resume = true;
        resumeOffset = position;
        watch.restart();
        interval.restart();
        String msg = "Resume downloading from host: " + host + " file: " + file + " at position: " + position + " bytes/"
                     + StringHelper.humanReadableBytes(position);
        if (fileSize > 0) {
            float percent = ((float) resumeOffset / (float) fileSize) * 100L;
            String num = String.format("%.1f", percent);
            msg += "/" + num + "% (size: " + fileSizeText + ")";
        }
        doLog(msg);
    }

    @Override
    public void onDownload(String host, String file, long chunkSize, long totalChunkSize, long fileSize) {
        totalChunkSize = totalChunkSize + resumeOffset;
        transferredBytes = totalChunkSize;

        String prefix = resume ? "Resume downloading" : "Downloading";
        String msg
                = prefix + " from host: " + host + " file: " + file + " chunk (" + chunkSize + "/" + totalChunkSize + " bytes)";
        if (fileSize > 0) {
            float percent = ((float) totalChunkSize / (float) fileSize) * 100L;
            String num = String.format("%.1f", percent);
            // avoid 100.0 as its only done when we get the onDownloadComplete
            if (totalChunkSize < fileSize && "100.0".equals(num)) {
                num = "99.9";
            }
            String size = StringHelper.humanReadableBytes(totalChunkSize);
            msg += " (progress: " + size + "/" + num + "%)";
        } else {
            // okay we do not know the total size, but then make what we have
            // download so-far human readable
            String size = StringHelper.humanReadableBytes(totalChunkSize);
            msg += " (downloaded: " + size + ")";
        }
        doLogVerbose(msg);
        // however if the operation is slow then log once in a while
        if (interval.taken() > endpoint.getTransferLoggingIntervalSeconds() * 1000) {
            doLog(msg);
            interval.restart();
        }
    }

    @Override
    public void onDownloadComplete(String host, String file) {
        String prefix = resume ? "Resume downloading" : "Downloading";
        String msg = prefix + " from host: " + host + " file: " + file + " completed";
        if (transferredBytes > 0) {
            msg += " (size: " + StringHelper.humanReadableBytes(transferredBytes) + ")";
        }
        long taken = watch.taken();
        String time = TimeUtils.printDuration(taken, true);
        msg += " (took: " + time + ")";
        doLog(msg);
    }

    @Override
    public void onBeginUploading(String host, String file) {
        download = false;
        watch.restart();
        interval.restart();
        String msg = "Uploading to host: " + host + " file: " + file + " starting";
        if (fileSize > 0) {
            msg += " (size: " + fileSizeText + ")";
        }
        doLog(msg);
    }

    @Override
    public void onUpload(String host, String file, long chunkSize, long totalChunkSize, long fileSize) {
        transferredBytes = totalChunkSize;

        String msg
                = "Uploading to host: " + host + " file: " + file + " chunk (" + chunkSize + "/" + totalChunkSize + " bytes)";
        if (fileSize > 0) {
            float percent = ((float) totalChunkSize / (float) fileSize) * 100L;
            String num = String.format("%.1f", percent);
            // avoid 100.0 as its only done when we get the onUploadComplete
            if (totalChunkSize < fileSize && "100.0".equals(num)) {
                num = "99.9";
            }
            String size = StringHelper.humanReadableBytes(totalChunkSize);
            msg += " (progress: " + size + "/" + num + "%)";
        } else {
            // okay we do not know the total size, but then make what we have
            // uploaded so-far human readable
            String size = StringHelper.humanReadableBytes(totalChunkSize);
            msg += " (uploaded: " + size + ")";
        }
        // each chunk is verbose
        doLogVerbose(msg);
        // however if the operation is slow then log once in a while
        if (interval.taken() > endpoint.getTransferLoggingIntervalSeconds() * 1000) {
            doLog(msg);
            interval.restart();
        }
    }

    @Override
    public void onUploadComplete(String host, String file) {
        String msg = "Uploading to host: " + host + " file: " + file + " completed";
        if (transferredBytes > 0) {
            msg += " (size: " + StringHelper.humanReadableBytes(transferredBytes) + ")";
        }
        long taken = watch.taken();
        String time = TimeUtils.printDuration(taken, true);
        msg += " (took: " + time + ")";
        doLog(msg);
    }

    @Override
    public void bytesTransferred(CopyStreamEvent event) {
        // not in use
    }

    @Override
    public void bytesTransferred(long totalBytesTransferred, int bytesTransferred, long streamSize) {
        // stream size is always -1, so use pre-calculated fileSize instead
        if (download) {
            onDownload(host, fileName, bytesTransferred, totalBytesTransferred, fileSize);
        } else {
            onUpload(host, fileName, bytesTransferred, totalBytesTransferred, fileSize);
        }
    }

    protected void doLog(String message) {
        lastLogActivity = message;
        lastLogActivityTimestamp = System.currentTimeMillis();
        // verbose implies regular log as well
        lastVerboseLogActivity = lastLogActivity;
        lastVerboseLogActivityTimestamp = lastLogActivityTimestamp;
        logger.log(message, endpoint.getTransferLoggingLevel());
    }

    protected void doLogVerbose(String message) {
        lastVerboseLogActivity = message;
        lastVerboseLogActivityTimestamp = System.currentTimeMillis();
        if (endpoint.isTransferLoggingVerbose()) {
            logger.log(message, endpoint.getTransferLoggingLevel());
        }
    }
}
