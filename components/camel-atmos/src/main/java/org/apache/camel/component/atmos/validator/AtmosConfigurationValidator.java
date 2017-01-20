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
package org.apache.camel.component.atmos.validator;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.component.atmos.AtmosConfiguration;
import org.apache.camel.component.atmos.util.AtmosException;
import org.apache.camel.component.atmos.util.AtmosOperation;
import static org.apache.camel.component.atmos.util.AtmosConstants.ATMOS_FILE_SEPARATOR;

public final class AtmosConfigurationValidator {

    private static final Pattern UNIX_PATH_PATTERN = Pattern.compile("/*?(\\S+)/*?", Pattern.CASE_INSENSITIVE);

    private AtmosConfigurationValidator() {
    }

    /**
     * Validate the parameters passed in the incoming url.
     *
     * @param configuration object containing the parameters.
     * @throws AtmosException
     */
    public static void validate(AtmosConfiguration configuration) throws AtmosException {
        validateCommonProperties(configuration);
        AtmosOperation op = configuration.getOperation();
        if (op == AtmosOperation.get) {
            validateGetOp(configuration);
        } else if (op == AtmosOperation.put) {
            validatePutOp(configuration);
        } else if (op == AtmosOperation.del) {
            validateDelOp(configuration);
        } else if (op == AtmosOperation.move) {
            validateMoveOp(configuration);
        }
    }

    private static void validateCommonProperties(AtmosConfiguration configuration) throws AtmosException {
        if (configuration.getFullTokenId() == null || configuration.getFullTokenId().equals("")) {
            throw new AtmosException("option <fullTokenId> is not present or not valid!");
        }
        if (configuration.getSecretKey() == null || configuration.getSecretKey().equals("")) {
            throw new AtmosException("option <secretKey> is not present or not valid!");
        }
        if (configuration.getUri() == null || configuration.getUri().equals("")) {
            throw new AtmosException("option <uri> is not present!");
        } else {
            try {
                URI uri = new URI(configuration.getUri());
            } catch (URISyntaxException use) {
                throw new AtmosException("option <uri> is not valid!", use);
            }
        }
    }

    private static void validateGetOp(AtmosConfiguration configuration) throws AtmosException {
        validateRemotePath(configuration.getRemotePath());
    }

    private static void validatePutOp(AtmosConfiguration configuration) throws AtmosException {
        validateLocalPath(configuration.getLocalPath());
        //remote path is optional
        if (configuration.getRemotePath() != null) {
            validateRemotePathForPut(configuration.getRemotePath());
        } else {  //in case remote path is not set, local path is even the remote path so it must be validated as UNIX
            validatePathInUnix(configuration.getLocalPath());
        }
    }

    private static void validateDelOp(AtmosConfiguration configuration) throws AtmosException {
        validateRemotePath(configuration.getRemotePath());
    }

    private static void validateMoveOp(AtmosConfiguration configuration) throws AtmosException {
        validateRemotePath(configuration.getRemotePath());
        validateRemotePath(configuration.getNewRemotePath());
    }

    private static void validateLocalPath(String localPath) throws AtmosException {
        if (localPath == null || localPath.equals("")) {
            throw new AtmosException("option <localPath> is not present or not valid!");
        }
        File file = new File(localPath);
        if (!file.exists()) {
            throw new AtmosException("option <localPath> is not an existing file or directory!");
        }
    }

    private static void validateRemotePath(String remotePath) throws AtmosException {
        if (remotePath == null || !remotePath.startsWith(ATMOS_FILE_SEPARATOR)) {
            throw new AtmosException("option <remotePath> is not valid!");
        }
        validatePathInUnix(remotePath);
    }

    private static void validateRemotePathForPut(String remotePath) throws AtmosException {
        if (!remotePath.startsWith(ATMOS_FILE_SEPARATOR)) {
            throw new AtmosException("option <remotePath> is not valid!");
        }
        validatePathInUnix(remotePath);
    }

    private static void validatePathInUnix(String path) throws AtmosException {
        Matcher matcher = UNIX_PATH_PATTERN.matcher(path);
        if (!matcher.matches()) {
            throw new AtmosException(path + " is not a valid path, must be in UNIX form!");
        }
    }

}
