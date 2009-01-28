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

import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.file.GenericFileOperations;


/**
 * Remote file operations based on some backing framework
 */
public interface RemoteFileOperations<T> extends GenericFileOperations<T> {

    /**
     * Connects to the remote server
     *
     * @param configuration configuraiton
     * @return true if connected
     * @throws GenericFileOperationFailedException can be thrown
     */
    boolean connect(RemoteFileConfiguration configuration) throws GenericFileOperationFailedException;

    /**
     * Returns whether we are connected to the remote server or not
     *
     * @return true if connected, false if not
     * @throws GenericFileOperationFailedException can be thrown
     */
    boolean isConnected() throws GenericFileOperationFailedException;

    /**
     * Discconects from the remote server
     *
     * @throws GenericFileOperationFailedException can be thrown
     */
    void disconnect() throws GenericFileOperationFailedException;

}
