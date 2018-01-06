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
import org.apache.commons.net.io.CopyStreamEvent;
import org.apache.commons.net.io.CopyStreamListener;

/**
 * Listener used for logging the progress of the upload or download of files.
 */
public class FtpCopyStreamListener implements CopyStreamListener {

    private final CamelLogger logger;
    private final String fileName;
    private final boolean download;

    public FtpCopyStreamListener(CamelLogger logger, String fileName, boolean download) {
        this.logger = logger;
        this.fileName = fileName;
        this.download = download;
    }

    @Override
    public void bytesTransferred(CopyStreamEvent event) {
        // not in use
    }

    @Override
    public void bytesTransferred(long totalBytesTransferred, int bytesTransferred, long streamSize) {
        // stream size is always -1 from the FTP client
        if (download) {
            logger.log("Downloading: " + fileName + " (chunk: " + bytesTransferred + ", total chunk: " + totalBytesTransferred + " bytes)");
        } else {
            logger.log("Uploading: " + fileName + " (chunk: " + bytesTransferred + ", total chunk: " + totalBytesTransferred + " bytes)");
        }
    }
}
