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

import org.apache.camel.component.dropbox.DropboxConfiguration;
import org.apache.camel.component.dropbox.producer.*;
import org.apache.camel.component.dropbox.util.DropboxException;
import org.apache.camel.component.dropbox.util.DropboxOperation;

import java.util.Map;

public class DropboxConfigurationValidator {

    public static void validate(DropboxConfiguration configuration) throws DropboxException{
        validateCommonProperties(configuration);
        DropboxOperation op = configuration.getOperation();
        if(op == DropboxOperation.get) {
            validateGetOp(configuration);
        }
        else if(op == DropboxOperation.put) {
            validatePutOp(configuration);
        }
        else if(op == DropboxOperation.search) {
            validateSearchOp(configuration);
        }
        else if(op == DropboxOperation.del) {
            validateDelOp(configuration);
        }
        else if(op == DropboxOperation.move) {
            validateMoveOp(configuration);
        }
    }

    private static void validateCommonProperties(DropboxConfiguration configuration) throws DropboxException {
        if(configuration.getAccessToken()==null || configuration.getAccessToken().equals("")) {
            throw new DropboxException("option <access token> is not present or not valid!");
        }
        if(configuration.getAppKey()==null || configuration.getAppKey().equals("")) {
            throw new DropboxException("option <app token> is not present or not valid!");
        }
        if(configuration.getAppSecret()==null || configuration.getAppSecret().equals("")) {
            throw new DropboxException("option <app secret> is not present or not valid!");
        }
    }

    private static void validateGetOp(DropboxConfiguration configuration) throws DropboxException {
        if(configuration.getRemotePath()==null || configuration.getRemotePath().equals("")) {
            throw new DropboxException("option <remote path> is not present or not valid!");
        }
    }

    private static void validatePutOp(DropboxConfiguration configuration) throws DropboxException {
        if(configuration.getLocalPath()==null || configuration.getLocalPath().equals("")) {
            throw new DropboxException("option <local path> is not present or not valid!");
        }
    }

    private static void validateSearchOp(DropboxConfiguration configuration) throws DropboxException {
        if(configuration.getRemotePath()==null || configuration.getRemotePath().equals("")) {
            throw new DropboxException("option <remote path> is not present or not valid!");
        }
    }

    private static void validateDelOp(DropboxConfiguration configuration) throws DropboxException {
        if(configuration.getRemotePath()==null || configuration.getRemotePath().equals("")) {
            throw new DropboxException("option <remote path> is not present or not valid!");
        }
    }

    private static void validateMoveOp(DropboxConfiguration configuration) throws DropboxException {
        if(configuration.getRemotePath()==null || configuration.getRemotePath().equals("")) {
            throw new DropboxException("option <remote path> is not present or not valid!");
        }
        if(configuration.getNewRemotePath()==null || configuration.getNewRemotePath().equals("")) {
            throw new DropboxException("option <new remote path> is not present or not valid!");
        }
    }
}
