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
package org.apache.camel.component.file.remote;

import org.apache.camel.Exchange;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.file.GenericFileOperations;
import org.apache.camel.component.file.strategy.FileMoveExistingStrategy;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.file.MoveExistingFileStrategyUtils.completePartialRelativePath;

public class SftpDefaultMoveExistingFileStrategy implements FileMoveExistingStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(SftpDefaultMoveExistingFileStrategy.class);

    /**
     * Moves any existing file due fileExists=Move is in use.
     */
    @Override
    public boolean moveExistingFile(GenericFileEndpoint<?> endpoint, GenericFileOperations<?> operations, String fileName)
            throws GenericFileOperationFailedException {
        // need to evaluate using a dummy and simulate the file first, to have
        // access to all the file attributes
        // create a dummy exchange as Exchange is needed for expression
        // evaluation
        // we support only the following 3 tokens.
        Exchange dummy = endpoint.createExchange();
        // we only support relative paths for the ftp component, so we strip out
        //any leading separator
        String parent = FileUtil.stripLeadingSeparator(FileUtil.onlyPath(fileName));
        String onlyName = FileUtil.stripPath(fileName);
        dummy.getIn().setHeader(FtpConstants.FILE_NAME, fileName);
        dummy.getIn().setHeader(FtpConstants.FILE_NAME_ONLY, onlyName);
        dummy.getIn().setHeader(FtpConstants.FILE_PARENT, parent);

        String to = endpoint.getMoveExisting().evaluate(dummy, String.class);

        // we only support relative paths for the ftp component, so strip any
        // leading paths
        to = FileUtil.stripLeadingSeparator(to);

        if (ObjectHelper.isEmpty(to)) {
            throw new GenericFileOperationFailedException(
                    "moveExisting evaluated as empty String, cannot move existing file: " + fileName);
        }

        to = completePartialRelativePath(to, onlyName, parent);

        // normalize accordingly to configuration
        to = ((SftpEndpoint) endpoint).getConfiguration().normalizePath(to);

        // do we have a sub directory
        String dir = FileUtil.onlyPath(to);
        if (dir != null) {
            // ensure directory exists
            operations.buildDirectory(dir, false);
        }

        // deal if there already exists a file
        if (operations.existsFile(to)) {
            if (endpoint.isEagerDeleteTargetFile()) {
                LOG.trace("Deleting existing file: {}", to);
                operations.deleteFile(to);
            } else {
                throw new GenericFileOperationFailedException(
                        "Cannot move existing file from: " + fileName + " to: " + to + " as there already exists a file: "
                                                              + to);
            }
        }

        LOG.trace("Moving existing file: {} to: {}", fileName, to);
        if (!operations.renameFile(fileName, to)) {
            throw new GenericFileOperationFailedException("Cannot rename file from: " + fileName + " to: " + to);
        }

        return true;
    }

}
