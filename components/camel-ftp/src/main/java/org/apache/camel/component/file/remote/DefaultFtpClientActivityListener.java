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

import org.apache.camel.util.CamelLogger;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.net.io.CopyStreamEvent;
import org.apache.commons.net.io.CopyStreamListener;

public class DefaultFtpClientActivityListener implements FtpClientActivityListener, CopyStreamListener {

    private final CamelLogger logger;
    private final String host;
    private final boolean verbose;
    private boolean download = true;

    private String fileName;
    private String lastLogActivity;
    private String lastVerboseLogActivity;
    private long lastLogActivityTimestamp = -1;
    private long lastVerboseLogActivityTimestamp = -1;
    private long transferredBytes;

    public DefaultFtpClientActivityListener(CamelLogger logger, boolean verbose, String host) {
        this.logger = logger;
        this.host = host;
        this.verbose = verbose;
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
        String msg = "Downloading from host: " + host + " file: " + file + " starting";
        doLog(msg);
    }

    @Override
    public void onDownload(String host, String file, long chunkSize, long totalChunkSize, long fileSize) {
        transferredBytes = totalChunkSize;

        String msg = "Downloading from host: " + host + " file: " + file + " chunk (" + chunkSize + "/" + totalChunkSize + " bytes)";
        if (fileSize > 0) {
            msg += " (file-size: " + fileSize + " bytes)";
        }
        doLog(msg);
    }

    @Override
    public void onDownloadComplete(String host, String file) {
        String msg = "Downloading from host: " + host + " file: " + file + " completed";
        if (transferredBytes > 0) {
            msg += " (" + transferredBytes + " bytes)";
        }
        doLog(msg);
    }

    @Override
    public void onBeginUploading(String host, String file) {
        download = false;
        String msg = "Uploading to host: " + host + " file: " + file + " starting";
        doLog(msg);
    }

    @Override
    public void onUpload(String host, String file, long chunkSize, long totalChunkSize, long fileSize) {
        transferredBytes = totalChunkSize;

        String msg = "Uploading to host: " + host + " file: " + file + " chunk (" + chunkSize + "/" + totalChunkSize + " bytes)";
        if (fileSize > 0) {
            msg += " (file-size: " + fileSize + " bytes)";
        }
        doLog(msg);
    }

    @Override
    public void onUploadComplete(String host, String file) {
        String msg = "Uploading to host: " + host + " file: " + file + " completed";
        if (transferredBytes > 0) {
            msg += " (" + transferredBytes + " bytes)";
        }
        doLog(msg);
    }

    @Override
    public void bytesTransferred(CopyStreamEvent event) {
        // not in use
    }

    @Override
    public void bytesTransferred(long totalBytesTransferred, int bytesTransferred, long streamSize) {
        if (download) {
            onDownload(host, fileName, bytesTransferred, totalBytesTransferred, streamSize);
        } else {
            onUpload(host, fileName, bytesTransferred, totalBytesTransferred, streamSize);
        }
    }

    protected void doLog(String message) {
        lastLogActivity = message;
        lastLogActivityTimestamp = System.currentTimeMillis();
        // verbose implies regular log as well
        lastVerboseLogActivity = lastLogActivity;
        lastVerboseLogActivityTimestamp = lastLogActivityTimestamp;
        logger.log(message);
    }

    protected void doLogVerbose(String message) {
        lastVerboseLogActivity = message;
        lastVerboseLogActivityTimestamp = System.currentTimeMillis();
        if (verbose) {
            logger.log(message);
        }
    }
}
