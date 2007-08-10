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

import java.io.File;

import org.apache.camel.component.file.FileEndpoint;
import org.apache.camel.component.file.FileExchange;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A strategy which will delete the file when its processed
 *
 * @version $Revision: 1.1 $
 */
public class DeleteFileProcessStrategy extends FileProcessStrategySupport {
    private static final transient Log LOG = LogFactory.getLog(DeleteFileProcessStrategy.class);

    public DeleteFileProcessStrategy() {
    }

    public DeleteFileProcessStrategy(boolean lockFile) {
        super(lockFile);
    }

    public void commit(FileEndpoint endpoint, FileExchange exchange, File file) throws Exception {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Deleting file: " + file);
        }
        file.delete();
        super.commit(endpoint, exchange, file);
    }
}
