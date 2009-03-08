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
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.net.ftp.FTPFile;

/**
 * FTP consumer
 */
public class FtpConsumer extends RemoteFileConsumer<FTPFile> {

    private String endpointPath;

    public FtpConsumer(RemoteFileEndpoint<FTPFile> endpoint, Processor processor, RemoteFileOperations<FTPFile> fileOperations) {
        super(endpoint, processor, fileOperations);
        this.endpointPath = endpoint.getConfiguration().getDirectory();
    }

    protected void pollDirectory(String fileName, List<GenericFile<FTPFile>> fileList) {
        if (fileName == null) {
            return;
        }

        // remove trailing /
        fileName = FileUtil.stripTrailingSeparator(fileName);

        if (log.isTraceEnabled()) {
            log.trace("Polling directory: " + fileName);
        }
        List<FTPFile> files = operations.listFiles(fileName);
        for (FTPFile file : files) {
            if (file.isDirectory()) {
                RemoteFile<FTPFile> remote = asRemoteFile(fileName, file);
                if (endpoint.isRecursive() && isValidFile(remote, true)) {
                    // recursive scan and add the sub files and folders
                    String directory = fileName + "/" + file.getName();
                    pollDirectory(directory, fileList);
                }
            } else if (file.isFile()) {
                RemoteFile<FTPFile> remote = asRemoteFile(fileName, file);
                if (isValidFile(remote, false)) {
                    // matched file so add
                    fileList.add(remote);
                }
            } else {
                log.debug("Ignoring unsupported remote file type: " + file);
            }
        }
    }

    protected void pollFile(String fileName, List<GenericFile<FTPFile>> fileList) {
        String directory = ".";
        int index = fileName.lastIndexOf("/");
        if (index > -1) {
            directory = fileName.substring(0, index);
        }
        // list the files in the fold and poll the first file
        List<FTPFile> list = operations.listFiles(fileName);
        if (list.size() > 0) {
            FTPFile file = list.get(0);
            if (file != null) {
                RemoteFile<FTPFile> remoteFile = asRemoteFile(directory, file);
                if (isValidFile(remoteFile, false)) {
                    // matched file so add
                    fileList.add(remoteFile);
                }
            }
        } else {
            if (log.isTraceEnabled()) {
                log.trace("Polled [" + fileName + "]. No files found");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private RemoteFile<FTPFile> asRemoteFile(String directory, FTPFile file) {
        RemoteFile<FTPFile> answer = new RemoteFile<FTPFile>();

        answer.setEndpointPath(endpointPath);
        answer.setFile(file);
        answer.setFileName(file.getName());
        answer.setFileNameOnly(file.getName());
        answer.setFileLength(file.getSize());
        if (file.getTimestamp() != null) {
            answer.setLastModified(file.getTimestamp().getTimeInMillis());
        }
        answer.setHostname(((RemoteFileConfiguration) endpoint.getConfiguration()).getHost());

        // all ftp files is considered as relative
        answer.setAbsolute(false);

        // create a pseudo absolute name
        String absoluteFileName = (ObjectHelper.isNotEmpty(directory) ? directory + "/" : "") + file.getName();
        answer.setAbsoluteFilePath(absoluteFileName);

        // the relative filename, skip the leading endpoint configured path
        String relativePath = ObjectHelper.after(absoluteFileName, endpointPath);
        // skip trailing /
        relativePath = FileUtil.stripLeadingSeparator(relativePath);
        answer.setRelativeFilePath(relativePath);

        return answer;
    }

}
