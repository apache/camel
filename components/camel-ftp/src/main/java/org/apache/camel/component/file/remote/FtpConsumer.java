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
import org.apache.camel.util.URISupport;
import org.apache.commons.net.ftp.FTPFile;

/**
 * FTP consumer
 */
public class FtpConsumer extends RemoteFileConsumer<FTPFile> {

    protected String endpointPath;

    public FtpConsumer(RemoteFileEndpoint<FTPFile> endpoint, Processor processor, RemoteFileOperations<FTPFile> fileOperations) {
        super(endpoint, processor, fileOperations);
        this.endpointPath = endpoint.getConfiguration().getDirectory();
    }

    @Override
    protected boolean pollDirectory(String fileName, List<GenericFile<FTPFile>> fileList, int depth) {
        String currentDir = null;
        if (isStepwise()) {
            // must remember current dir so we stay in that directory after the poll
            currentDir = operations.getCurrentDirectory();
        }

        // strip trailing slash
        fileName = FileUtil.stripTrailingSeparator(fileName);

        boolean answer = doPollDirectory(fileName, null, fileList, depth);
        if (currentDir != null) {
            operations.changeCurrentDirectory(currentDir);
        }

        return answer;
    }

    protected boolean pollSubDirectory(String absolutePath, String dirName, List<GenericFile<FTPFile>> fileList, int depth) {
        boolean answer = doPollDirectory(absolutePath, dirName, fileList, depth);
        // change back to parent directory when finished polling sub directory
        if (isStepwise()) {
            operations.changeToParentDirectory();
        }
        return answer;
    }

    protected boolean doPollDirectory(String absolutePath, String dirName, List<GenericFile<FTPFile>> fileList, int depth) {
        log.trace("doPollDirectory from absolutePath: {}, dirName: {}", absolutePath, dirName);

        depth++;

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
        List<FTPFile> files;
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

        for (FTPFile file : files) {

            // check if we can continue polling in files
            if (!canPollMoreFiles(fileList)) {
                return false;
            }

            if (file.isDirectory()) {
                RemoteFile<FTPFile> remote = asRemoteFile(absolutePath, file);
                if (endpoint.isRecursive() && isValidFile(remote, true) && depth < endpoint.getMaxDepth()) {
                    // recursive scan and add the sub files and folders
                    String subDirectory = file.getName();
                    String path = absolutePath + "/" + subDirectory;
                    boolean canPollMore = pollSubDirectory(path, subDirectory, fileList, depth);
                    if (!canPollMore) {
                        return false;
                    }
                }
            } else if (file.isFile()) {
                RemoteFile<FTPFile> remote = asRemoteFile(absolutePath, file);
                if (isValidFile(remote, false) && depth >= endpoint.getMinDepth()) {
                    if (isInProgress(remote)) {
                        log.trace("Skipping as file is already in progress: {}", remote.getFileName());
                    } else {
                        // matched file so add
                        fileList.add(remote);
                    }
                }
            } else {
                log.debug("Ignoring unsupported remote file type: " + file);
            }
        }

        return true;
    }

    private RemoteFile<FTPFile> asRemoteFile(String absolutePath, FTPFile file) {
        RemoteFile<FTPFile> answer = new RemoteFile<FTPFile>();

        answer.setEndpointPath(endpointPath);
        answer.setFile(file);
        answer.setFileNameOnly(file.getName());
        answer.setFileLength(file.getSize());
        answer.setDirectory(file.isDirectory());
        if (file.getTimestamp() != null) {
            answer.setLastModified(file.getTimestamp().getTimeInMillis());
        }
        answer.setHostname(((RemoteFileConfiguration) endpoint.getConfiguration()).getHost());

        // absolute or relative path
        boolean absolute = FileUtil.hasLeadingSeparator(absolutePath);
        answer.setAbsolute(absolute);

        // create a pseudo absolute name
        String dir = FileUtil.stripTrailingSeparator(absolutePath);
        String absoluteFileName = FileUtil.stripLeadingSeparator(dir + "/" + file.getName());
        // if absolute start with a leading separator otherwise let it be relative
        if (absolute) {
            absoluteFileName = "/" + absoluteFileName;
        }
        answer.setAbsoluteFilePath(absoluteFileName);

        // the relative filename, skip the leading endpoint configured path
        String relativePath = ObjectHelper.after(absoluteFileName, endpointPath);
        // skip leading /
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

    @Override
    public String toString() {
        return "FtpConsumer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
    }
}
