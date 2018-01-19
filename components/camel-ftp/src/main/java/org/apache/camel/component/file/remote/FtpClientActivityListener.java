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

import org.apache.commons.net.io.CopyStreamListener;

/**
 * Listener that captures the activity of the FTP Client such as connecting, login, upload and download etc.
 */
public interface FtpClientActivityListener extends CopyStreamListener {

    String getLastLogActivity();

    long getLastLogActivityTimestamp();

    String getLastVerboseLogActivity();

    long getLastVerboseLogActivityTimestamp();

    /**
     * Whether in download or upload mode
     */
    void setDownload(boolean download);

    void setRemoteFileName(String fileName);

    void setRemoteFileSize(long size);

    void onGeneralError(String host, String errorMessage);

    void onConnecting(String host);

    void onConnected(String host);

    void onLogin(String host);

    void onLoginComplete(String host);

    void onLoginFailed(int replyCode, String replyMessage);

    void onDisconnecting(String host);

    void onDisconnected(String host);

    void onScanningForFiles(String host, String directory);

    void onBeginDownloading(String host, String file);

    void onResumeDownloading(String host, String file, long position);

    void onDownload(String host, String file, long chunkSize, long totalChunkSize, long fileSize);

    void onDownloadComplete(String host, String file);

    void onBeginUploading(String host, String file);

    void onUpload(String host, String file, long chunkSize, long totalChunkSize, long fileSize);

    void onUploadComplete(String host, String file);

}
