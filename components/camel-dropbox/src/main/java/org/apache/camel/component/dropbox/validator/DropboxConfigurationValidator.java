/*
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
package org.apache.camel.component.dropbox.validator;

import java.io.File;

import org.apache.camel.component.dropbox.DropboxConfiguration;
import org.apache.camel.component.dropbox.util.DropboxConstants;
import org.apache.camel.component.dropbox.util.DropboxException;
import org.apache.camel.component.dropbox.util.DropboxUploadMode;
import org.apache.camel.util.ObjectHelper;

public final class DropboxConfigurationValidator {

    private DropboxConfigurationValidator() { }

    public static void validateCommonProperties(DropboxConfiguration configuration) throws DropboxException {
        if (configuration.getAccessToken() == null || configuration.getAccessToken().equals("")) {
            throw new DropboxException("option <accessToken> is not present or not valid!");
        }
        if (configuration.getClientIdentifier() == null || configuration.getClientIdentifier().equals("")) {
            throw new DropboxException("option <clientIdentifier> is not present or not valid!");
        }
    }

    public static void validateGetOp(String remotePath) throws DropboxException {
        validateRemotePath(remotePath);
    }

    public static void validatePutOp(String localPath, String remotePath, DropboxUploadMode uploadMode) throws DropboxException {
        validateLocalPath(localPath);
        //remote path is optional
        if (remotePath != null) {
            validateRemotePathForPut(remotePath);
        } else {  //in case remote path is not set, local path is even the remote path so it must be validated as UNIX
            validatePathInUnix(localPath);
        }
        if (uploadMode == null) {
            throw new DropboxException("option <uploadMode> is not present or not valid!");
        }
    }

    public static void validateSearchOp(String remotePath) throws DropboxException {
        validateRemotePath(remotePath);
    }

    public static void validateDelOp(String remotePath) throws DropboxException {
        validateRemotePath(remotePath);
    }

    public static void validateMoveOp(String remotePath, String newRemotePath) throws DropboxException {
        validateRemotePath(remotePath);
        validateRemotePath(newRemotePath);
    }

    private static void validateLocalPath(String localPath) throws DropboxException {
        if (ObjectHelper.isNotEmpty(localPath)) {
            File file = new File(localPath);
            if (!file.exists()) {
                throw new DropboxException("option <localPath> is not an existing file or directory!");
            }
        }
    }

    private static void validateRemotePath(String remotePath) throws DropboxException {
        if (remotePath == null || !remotePath.startsWith(DropboxConstants.DROPBOX_FILE_SEPARATOR)) {
            throw new DropboxException("option <remotePath> is not valid!");
        }
        validatePathInUnix(remotePath);
    }

    private static void validateRemotePathForPut(String remotePath) throws DropboxException {
        if (!remotePath.startsWith(DropboxConstants.DROPBOX_FILE_SEPARATOR)) {
            throw new DropboxException("option <remotePath> is not valid!");
        }
        validatePathInUnix(remotePath);
    }

    private static void validatePathInUnix(String path) throws DropboxException {
        if (path.indexOf('\\') != -1) {
            throw new DropboxException(path + " must not contain Windows path separator, use UNIX path separator!");
        }
    }

}
