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
import org.apache.camel.util.ObjectHelper;

/**
 * SFTP consumer
 */
public class SftpConsumer extends RemoteFileConsumer<ChannelSftp.LsEntry> {

    public SftpConsumer(RemoteFileEndpoint<ChannelSftp.LsEntry> endpoint, Processor processor, RemoteFileOperations<ChannelSftp.LsEntry> operations) {
        super(endpoint, processor, operations);
    }

    protected void pollDirectory(String fileName, List<GenericFile<ChannelSftp.LsEntry>> fileList) {
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
        List<ChannelSftp.LsEntry> files = operations.listFiles(fileName);
        for (ChannelSftp.LsEntry file : files) {
            RemoteFile<ChannelSftp.LsEntry> remote = asRemoteFile(fileName, file);
            if (file.getAttrs().isDir()) {
                if (endpoint.isRecursive() && isValidFile(remote, true)) {
                    // recursive scan and add the sub files and folders
                    String directory = fileName + "/" + file.getFilename();
                    pollDirectory(directory, fileList);
                }
                // we cannot use file.getAttrs().isLink on Windows, so we dont invoke the method
                // just assuming its a file we should poll
            } else {
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
        ChannelSftp.LsEntry file = list.get(0);
        if (file != null) {
            RemoteFile<ChannelSftp.LsEntry> remoteFile = asRemoteFile(directory, file);
            if (isValidFile(remoteFile, false)) {
                // matched file so add
                fileList.add(remoteFile);
            }
        }
    }

    private RemoteFile<ChannelSftp.LsEntry> asRemoteFile(String directory, ChannelSftp.LsEntry file) {
        RemoteFile<ChannelSftp.LsEntry> remote = new RemoteFile<ChannelSftp.LsEntry>();
        remote.setFile(file);
        remote.setFileName(file.getFilename());
        remote.setFileLength(file.getAttrs().getSize());
        remote.setLastModified(file.getAttrs().getMTime() * 1000L);
        remote.setHostname(((RemoteFileConfiguration) endpoint.getConfiguration()).getHost());
        String absoluteFileName = (ObjectHelper.isNotEmpty(directory) ? directory + "/" : "") + file.getFilename();
        remote.setAbsoluteFileName(absoluteFileName);

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
