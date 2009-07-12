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
package org.apache.camel.component.file.strategy;

import org.apache.camel.Exchange;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.file.GenericFileOperations;

public class GenericFileDeleteProcessStrategy<T> extends GenericFileProcessStrategySupport<T> {

    @Override
    public void commit(GenericFileOperations<T> operations, GenericFileEndpoint<T> endpoint, Exchange exchange, GenericFile<T> file) throws Exception {
        // must invoke super
        super.commit(operations, endpoint, exchange, file);

        int retries = 3;
        boolean deleted = false;

        while (retries > 0 && !deleted) {
            retries--;

            if (operations.deleteFile(file.getAbsoluteFilePath())) {
                // file is deleted
                deleted = true;
                break;
            }

            // some OS can report false when deleting but the file is still deleted
            // use exists to check instead
            boolean exits = operations.existsFile(file.getAbsoluteFilePath());
            if (!exits) {
                deleted = true;
            } else {
                if (log.isTraceEnabled()) {
                    log.trace("File was not deleted at this attempt will try again in 1 sec.: " + file);
                }
                // sleep a bit and try again
                Thread.sleep(1000);
            }
        }

        if (!deleted) {
            throw new GenericFileOperationFailedException("Cannot delete file: " + file);
        }
    }

}