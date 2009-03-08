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
import org.apache.camel.Processor;
import org.apache.camel.component.file.GenericFile;
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

    protected void pollDirectory(String fileName, List<GenericFile<ChannelSftp.LsEntry>> fileList) {
        if (fileName == null) {
            return;
        }

        // remove trailing /
        fileName = FileUtil.stripTrailingSeparator(fileName);

        if (log.isTraceEnabled()) {
            log.trace("Polling directory: " + fileName);
        }
        List<ChannelSftp.LsEntry> files = operations.listFiles(fileName);
        for (ChannelSftp.LsEntry file : files) {
            if (file.getAttrs().isDir()) {
                RemoteFile<ChannelSftp.LsEntry> remote = asRemoteFile(fileName, file);
                if (endpoint.isRecursive() && isValidFile(remote, true)) {
                    // recursive scan and add the sub files and folders
                    String directory = fileName + "/" + file.getFilename();
                    pollDirectory(directory, fileList);
                }
                // we cannot use file.getAttrs().isLink on Windows, so we dont invoke the method
                // just assuming its a file we should poll
            } else {
                RemoteFile<ChannelSftp.LsEntry> remote = asRemoteFile(fileName, file);
                if (isValidFile(remote, false)) {
                    // matched file so add
                    fileList.add(remote);
                }
            }
        }
    }

    /**
     * Polls the given file
     *
     * @param fileName the file name
     * @param fileList current list of files gathered
     */
    protected void pollFile(String fileName, List<GenericFile<ChannelSftp.LsEntry>> fileList) {
        String directory = ".";
        int index = fileName.lastIndexOf("/");
        if (index > -1) {
            directory = fileName.substring(0, index);
        }
        // list the files in the fold and poll the first file
        List<ChannelSftp.LsEntry> list = operations.listFiles(fileName);
        if (list.size() > 0) {
            ChannelSftp.LsEntry file = list.get(0);
            if (file != null) {
                RemoteFile<ChannelSftp.LsEntry> remoteFile = asRemoteFile(directory, file);
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

    private RemoteFile<ChannelSftp.LsEntry> asRemoteFile(String directory, ChannelSftp.LsEntry file) {
        RemoteFile<ChannelSftp.LsEntry> answer = new RemoteFile<ChannelSftp.LsEntry>();

        answer.setEndpointPath(endpointPath);
        answer.setFile(file);
        answer.setFileName(file.getFilename());
        answer.setFileNameOnly(file.getFilename());
        answer.setFileLength(file.getAttrs().getSize());
        answer.setLastModified(file.getAttrs().getMTime() * 1000L);
        answer.setHostname(((RemoteFileConfiguration) endpoint.getConfiguration()).getHost());

        // all ftp files is considered as relative
        answer.setAbsolute(false);

        // create a pseudo absolute name
        String absoluteFileName = (ObjectHelper.isNotEmpty(directory) ? directory + "/" : "") + file.getFilename();
        answer.setAbsoluteFilePath(absoluteFileName);

        // the relative filename, skip the leading endpoint configured path
        String relativePath = ObjectHelper.after(absoluteFileName, endpointPath);
        // skip trailing /
        relativePath = FileUtil.stripLeadingSeparator(relativePath);
        answer.setRelativeFilePath(relativePath);

        return answer;
    }

}
