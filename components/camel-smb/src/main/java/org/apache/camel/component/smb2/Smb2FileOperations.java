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
package org.apache.camel.component.smb2;

import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import org.apache.camel.Exchange;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.file.GenericFileOperations;

public interface Smb2FileOperations extends GenericFileOperations<FileIdBothDirectoryInformation> {

    /**
     * Connects to the remote server
     *
     * @param  configuration                       configuration
     * @param  exchange                            the exchange that trigger the connect (if any)
     * @return                                     <tt>true</tt> if connected
     * @throws GenericFileOperationFailedException can be thrown
     */
    boolean connect(Smb2Configuration configuration, Exchange exchange) throws GenericFileOperationFailedException;

    /**
     * Returns whether we are connected to the remote server or not
     *
     * @return                                     <tt>true</tt> if connected, <tt>false</tt> if not
     * @throws GenericFileOperationFailedException can be thrown
     */
    boolean isConnected() throws GenericFileOperationFailedException;

    /**
     * Disconnects from the remote server
     *
     * @throws GenericFileOperationFailedException can be thrown
     */
    void disconnect() throws GenericFileOperationFailedException;

    /**
     * Forces a hard disconnect from the remote server and cause the client to be re-created on next poll.
     *
     * @throws GenericFileOperationFailedException can be thrown
     */
    void forceDisconnect() throws GenericFileOperationFailedException;

}
