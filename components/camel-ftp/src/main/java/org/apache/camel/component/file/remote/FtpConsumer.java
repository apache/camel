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

import org.apache.camel.Processor;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.net.ftp.FTPFile;

/**
 * FTP consumer
 */
public class FtpConsumer extends RemoteFileConsumer {

    public FtpConsumer(RemoteFileEndpoint endpoint, Processor processor, RemoteFileOperations ftp) {
        super(endpoint, processor, ftp);
    }

    protected void pollDirectory(String fileName, List<RemoteFile> fileList) {
        if (fileName == null) {
            return;
        }

        // fix filename
        if (fileName.endsWith("/")) {
            fileName = fileName.substring(0, fileName.length() - 1);
        }

        if (log.isTraceEnabled()) {
            log.trace("Polling directory: " + fileName);
        }
        List<FTPFile> files = operations.listFiles(fileName);
        for (FTPFile file : files) {
            RemoteFile<FTPFile> remote = asRemoteFile(fileName, file);
            if (file.isDirectory()) {
                if (endpoint.isRecursive() && isValidFile(remote, true)) {
                    // recursive scan and add the sub files and folders
                    String directory = fileName + "/" + file.getName();
                    pollDirectory(directory, fileList);
                }
            } else if (file.isFile()) {
                if (isValidFile(remote, false)) {
                    // matched file so add
                    fileList.add(remote);
                }
            } else {
                log.debug("Ignoring unsupported remote file type: " + file);
            }
        }
    }

    protected void pollFile(String fileName, List<RemoteFile> fileList) {
        String directory = ".";
        int index = fileName.lastIndexOf("/");
        if (index > -1) {
            directory = fileName.substring(0, index);
        }
        // list the files in the fold and poll the first file
        List<FTPFile> list = operations.listFiles(fileName);
        FTPFile file = list.get(0);
        if (file != null) {
            RemoteFile remoteFile = asRemoteFile(directory, file);
            if (isValidFile(remoteFile, false)) {
                // matched file so add
                fileList.add(remoteFile);
            }
        }
    }

    private RemoteFile<FTPFile> asRemoteFile(String directory, FTPFile file) {
        RemoteFile<FTPFile> remote = new RemoteFile<FTPFile>();
        remote.setFile(file);
        remote.setFileName(file.getName());
        remote.setFileLength(file.getSize());
        if (file.getTimestamp() != null) {
            remote.setLastModified(file.getTimestamp().getTimeInMillis());
        }
        remote.setHostname(endpoint.getConfiguration().getHost());
        String absoluteFileName = (ObjectHelper.isNotEmpty(directory) ? directory + "/" : "") + file.getName();
        remote.setAbsolutelFileName(absoluteFileName);

        // the relative filename
        String ftpBasePath = endpoint.getConfiguration().getFile();
        String relativePath = absoluteFileName.substring(ftpBasePath.length());
        if (relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }
        remote.setRelativeFileName(relativePath);

        return remote;
    }

}
