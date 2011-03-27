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

    protected boolean pollDirectory(String fileName, List<GenericFile<ChannelSftp.LsEntry>> fileList) {
        String currentDir = null;
        if (isStepwise()) {
            // must remember current dir so we stay in that directory after the poll
            currentDir = operations.getCurrentDirectory();
        }

        // strip trailing slash
        fileName = FileUtil.stripTrailingSeparator(fileName);

        boolean answer = doPollDirectory(fileName, null, fileList);
        if (currentDir != null) {
            operations.changeCurrentDirectory(currentDir);
        }

        return answer;
    }

    protected boolean pollSubDirectory(String absolutePath, String dirName, List<GenericFile<ChannelSftp.LsEntry>> fileList) {
        boolean answer = doPollDirectory(absolutePath, dirName, fileList);
        // change back to parent directory when finished polling sub directory
        if (isStepwise()) {
            operations.changeToParentDirectory();
        }
        return answer;
    }

    protected boolean doPollDirectory(String absolutePath, String dirName, List<GenericFile<ChannelSftp.LsEntry>> fileList) {
        log.trace("doPollDirectory from absolutePath: {}, dirName: {}", absolutePath, dirName);

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
        List<ChannelSftp.LsEntry> files;
        if (isStepwise()) {
            files = operations.listFiles();
        } else {
            files = operations.listFiles(dir);
        }
        if (files == null || files.isEmpty()) {
            // no files in this directory to poll
            log.trace("No files found in directory: {}", dir);
            return true;
        } else {
            // we found some files
            log.trace("Found {} in directory: {}", files.size(), dir);
        }

        for (ChannelSftp.LsEntry file : files) {

            // check if we can continue polling in files
            if (!canPollMoreFiles(fileList)) {
                return false;
            }

            if (file.getAttrs().isDir()) {
                RemoteFile<ChannelSftp.LsEntry> remote = asRemoteFile(absolutePath, file);
                if (endpoint.isRecursive() && isValidFile(remote, true)) {
                    // recursive scan and add the sub files and folders
                    String subDirectory = file.getFilename();
                    String path = absolutePath + "/" + subDirectory;
                    boolean canPollMore = pollSubDirectory(path, subDirectory, fileList);
                    if (!canPollMore) {
                        return false;
                    }
                }
                // we cannot use file.getAttrs().isLink on Windows, so we dont invoke the method
                // just assuming its a file we should poll
            } else {
                RemoteFile<ChannelSftp.LsEntry> remote = asRemoteFile(absolutePath, file);
                if (isValidFile(remote, false)) {
                    if (isInProgress(remote)) {
                        if (log.isTraceEnabled()) {
                            log.trace("Skipping as file is already in progress: {}", remote.getFileName());
                        }
                    } else {
                        // matched file so add
                        fileList.add(remote);
                    }
                }
            }
        }

        return true;
    }

    private RemoteFile<ChannelSftp.LsEntry> asRemoteFile(String absolutePath, ChannelSftp.LsEntry file) {
        RemoteFile<ChannelSftp.LsEntry> answer = new RemoteFile<ChannelSftp.LsEntry>();

        answer.setEndpointPath(endpointPath);
        answer.setFile(file);
        answer.setFileNameOnly(file.getFilename());
        answer.setFileLength(file.getAttrs().getSize());
        answer.setLastModified(file.getAttrs().getMTime() * 1000L);
        answer.setHostname(((RemoteFileConfiguration) endpoint.getConfiguration()).getHost());

        // absolute or relative path
        boolean absolute = FileUtil.hasLeadingSeparator(absolutePath);
        answer.setAbsolute(absolute);

        // create a pseudo absolute name
        String dir = FileUtil.stripTrailingSeparator(absolutePath);
        String absoluteFileName = FileUtil.stripLeadingSeparator(dir + "/" + file.getFilename());
        // if absolute start with a leading separator otherwise let it be relative
        if (absolute) {
            absoluteFileName = "/" + absoluteFileName;
        }
        answer.setAbsoluteFilePath(absoluteFileName);

        // the relative filename, skip the leading endpoint configured path
        String relativePath = ObjectHelper.after(absoluteFileName, endpointPath);
        // skip trailing /
        relativePath = FileUtil.stripLeadingSeparator(relativePath);
        answer.setRelativeFilePath(relativePath);

        // the file name should be the relative path
        answer.setFileName(answer.getRelativeFilePath());

        return answer;
    }

    private boolean isStepwise() {
        RemoteFileConfiguration config = (RemoteFileConfiguration) endpoint.getConfiguration();
        return config.isStepwise();
    }

}
