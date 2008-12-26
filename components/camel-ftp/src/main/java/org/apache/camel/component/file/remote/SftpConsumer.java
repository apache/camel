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

/**
 * SFTP consumer
 */
public class SftpConsumer extends RemoteFileConsumer {

    public SftpConsumer(RemoteFileEndpoint endpoint, Processor processor, RemoteFileOperations remoteFileOperations) {
        super(endpoint, processor, remoteFileOperations);
    }

    protected void pollDirectory(String fileName, boolean processDir, List<RemoteFile> fileList) {
        if (fileName == null) {
            return;
        }

        String currentDir = operations.getCurrentDirectory();
        operations.changeCurrentDirectory(fileName);

        if (log.isTraceEnabled()) {
            log.trace("Polling directory: " + fileName);
        }
        List<ChannelSftp.LsEntry> files = operations.listFiles();
        for (ChannelSftp.LsEntry file : files) {
            RemoteFile<ChannelSftp.LsEntry> remote = asRemoteFile(file);
            if (processDir && file.getAttrs().isDir() && isValidFile(remote, true)) {
                // recursive scan and add the sub files and folders
                pollDirectory(file.getFilename(), endpoint.isRecursive(), fileList);
            } else if (!file.getAttrs().isLink() && isValidFile(remote, false)) {
                // matched file so add
                fileList.add(remote);
            } else {
                log.debug("Ignoring unsupported file type " + file);
            }
        }

        operations.changeCurrentDirectory(currentDir);
    }

    /**
     * Polls the given file
     *
     * @param fileName  the file name
     * @param fileList  current list of files gathered
     */
    protected void pollFile(String fileName, List<RemoteFile> fileList) {
        int index = fileName.lastIndexOf("/");
        if (index > -1) {
            // cd to the folder of the filename
            operations.changeCurrentDirectory(fileName.substring(0, index));
        }
        // list the files in the fold and poll the first file
        List<ChannelSftp.LsEntry> list = operations.listFiles(fileName.substring(index + 1));
        ChannelSftp.LsEntry file = list.get(0);
        if (file != null) {
            RemoteFile remoteFile = asRemoteFile(file);
            fileList.add(remoteFile);
        }
    }

    private RemoteFile<ChannelSftp.LsEntry> asRemoteFile(ChannelSftp.LsEntry file) {
        RemoteFile<ChannelSftp.LsEntry> remote = new RemoteFile<ChannelSftp.LsEntry>();
        remote.setFile(file);
        remote.setFileName(file.getFilename());
        remote.setFileLength(file.getAttrs().getSize());
        remote.setLastModified(file.getAttrs().getMTime() * 1000L);
        remote.setHostname(endpoint.getConfiguration().getHost());
        String absoluteFileName = getAbsoluteFileName(file);
        remote.setAbsolutelFileName(absoluteFileName);

        // the relative filename
        String ftpBasePath = endpoint.getConfiguration().getFile();
        String relativePath = absoluteFileName.substring(ftpBasePath.length() + 1);
        if (relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }
        remote.setRelativeFileName(relativePath);

        return remote;
    }

    private String getAbsoluteFileName(ChannelSftp.LsEntry ftpFile) {
        return operations.getCurrentDirectory() + "/" + ftpFile.getFilename();
    }

}
