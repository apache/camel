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
package org.apache.camel.component.dropbox.validator;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.component.dropbox.DropboxConfiguration;
import org.apache.camel.component.dropbox.util.DropboxException;
import org.apache.camel.component.dropbox.util.DropboxOperation;


import static org.apache.camel.component.dropbox.util.DropboxConstants.DROPBOX_FILE_SEPARATOR;

public final class DropboxConfigurationValidator {

    private DropboxConfigurationValidator() { }

    /**
     * Validate the parameters passed in the incoming url.
     * @param configuration object containing the parameters.
     * @throws DropboxException
     */
    public static void validate(DropboxConfiguration configuration) throws DropboxException {
        validateCommonProperties(configuration);
        DropboxOperation op = configuration.getOperation();
        if (op == DropboxOperation.get) {
            validateGetOp(configuration);
        } else if (op == DropboxOperation.put) {
            validatePutOp(configuration);
        } else if (op == DropboxOperation.search) {
            validateSearchOp(configuration);
        } else if (op == DropboxOperation.del) {
            validateDelOp(configuration);
        } else if (op == DropboxOperation.move) {
            validateMoveOp(configuration);
        }
    }

    private static void validateCommonProperties(DropboxConfiguration configuration) throws DropboxException {
        if (configuration.getAccessToken() == null || configuration.getAccessToken().equals("")) {
            throw new DropboxException("option <accessToken> is not present or not valid!");
        }
        if (configuration.getClientIdentifier() == null || configuration.getClientIdentifier().equals("")) {
            throw new DropboxException("option <clientIdentifier> is not present or not valid!");
        }
    }

    private static void validateGetOp(DropboxConfiguration configuration) throws DropboxException {
        validateRemotePath(configuration.getRemotePath());
    }

    private static void validatePutOp(DropboxConfiguration configuration) throws DropboxException {
        validateLocalPath(configuration.getLocalPath());
        //remote path is optional
        if (configuration.getRemotePath() != null) {
            validateRemotePathForPut(configuration.getRemotePath());
        } else {  //in case remote path is not set, local path is even the remote path so it must be validated as UNIX
            validatePathInUnix(configuration.getLocalPath());
        }
        if (configuration.getUploadMode() == null) {
            throw new DropboxException("option <uploadMode> is not present or not valid!");
        }
    }

    private static void validateSearchOp(DropboxConfiguration configuration) throws DropboxException {
        validateRemotePath(configuration.getRemotePath());
    }

    private static void validateDelOp(DropboxConfiguration configuration) throws DropboxException {
        validateRemotePath(configuration.getRemotePath());
    }

    private static void validateMoveOp(DropboxConfiguration configuration) throws DropboxException {
        validateRemotePath(configuration.getRemotePath());
        validateRemotePath(configuration.getNewRemotePath());
    }

    private static void validateLocalPath(String localPath) throws DropboxException {
        if (localPath == null || localPath.equals("")) {
            throw new DropboxException("option <localPath> is not present or not valid!");
        }
        File file = new File(localPath);
        if (!file.exists()) {
            throw new DropboxException("option <localPath> is not an existing file or directory!");
        }
    }

    private static void validateRemotePath(String remotePath) throws DropboxException {
        if (remotePath == null || !remotePath.startsWith(DROPBOX_FILE_SEPARATOR)) {
            throw new DropboxException("option <remotePath> is not valid!");
        }
        validatePathInUnix(remotePath);
    }

    private static void validateRemotePathForPut(String remotePath) throws DropboxException {
        if (!remotePath.startsWith(DROPBOX_FILE_SEPARATOR)) {
            throw new DropboxException("option <remotePath> is not valid!");
        }
        validatePathInUnix(remotePath);
    }

    private static void validatePathInUnix(String path) throws DropboxException {
        Pattern pattern = Pattern.compile("/*?(\\S+)/*?", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(path);
        if (!matcher.matches()) {
            throw new DropboxException(path + " is not a valid path, must be in UNIX form!");
        }
    }

}
