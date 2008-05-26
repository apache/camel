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

import java.util.Properties;

import org.apache.camel.component.file.FileProcessStrategy;

/**
 * Factory to provide the {@link org.apache.camel.component.file.FileProcessStrategy} to use.
 */
public final class FileProcessStrategyFactory {

    private FileProcessStrategyFactory() {
        // Factory class
    }

    /**
     * A strategy method to lazily create the file strategy to use.
     */
    public static FileProcessStrategy createFileProcessStrategy(Properties params) {

        // We assume a value is present only if its value not null for String and 'true' for boolean
        boolean isDelete = params.getProperty("delete") != null;
        boolean isLock = params.getProperty("lock") != null;
        String moveNamePrefix = params.getProperty("moveNamePrefix");
        String moveNamePostfix = params.getProperty("moveNamePostfix");

        if (params.getProperty("noop") != null) {
            return new NoOpFileProcessStrategy(isLock);
        } else if (moveNamePostfix != null || moveNamePrefix != null) {
            if (isDelete) {
                throw new IllegalArgumentException("You cannot set the deleteFiles property "
                    + "and a moveFilenamePostfix or moveFilenamePrefix");
            }
            return new RenameFileProcessStrategy(isLock, moveNamePrefix, moveNamePostfix);
        } else if (isDelete) {
            return new DeleteFileProcessStrategy(isLock);
        } else {
            return new RenameFileProcessStrategy(isLock);
        }
    }
}
