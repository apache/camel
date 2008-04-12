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
package org.apache.camel.component.file;

import java.io.File;

/**
 * Represents a strategy for marking that a file is processed.
 *
 * @version $Revision$
 */
public interface FileProcessStrategy {

    /**
     * Called when work is about to begin on this file. This method may attempt to acquire some file lock before
     * returning true; returning false if the file lock could not be obtained so that the file should be ignored.
     *
     * @param endpoint  the endpoint
     * @param exchange  the exchange
     * @param file      the file
     * @return true if the file can be processed (such as if a file lock could be obtained)
     * @throws Exception can be thrown in case of errors
     */
    boolean begin(FileEndpoint endpoint, FileExchange exchange, File file) throws Exception;

    /**
     * Releases any file locks and possibly deletes or moves the file
     *
     * @param endpoint  the endpoint
     * @param exchange  the exchange
     * @param file      the file
     * @throws Exception can be thrown in case of errors
     */
    void commit(FileEndpoint endpoint, FileExchange exchange, File file) throws Exception;
    
}
