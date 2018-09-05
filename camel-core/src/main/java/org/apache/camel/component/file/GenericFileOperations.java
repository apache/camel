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

import java.util.List;

import org.apache.camel.Exchange;

public interface GenericFileOperations<T> {

    /**
     * Sets the endpoint as some implementations need access to the endpoint and how its configured.
     *
     * @param endpoint the endpoint
     */
    void setEndpoint(GenericFileEndpoint<T> endpoint);

    /**
     * Deletes the file name by name, relative to the current directory
     *
     * @param name name of the file
     * @return true if deleted, false if not
     * @throws GenericFileOperationFailedException can be thrown
     */
    boolean deleteFile(String name) throws GenericFileOperationFailedException;

    /**
     * Determines whether the files exists or not
     *
     * @param name name of the file
     * @return true if exists, false if not
     * @throws GenericFileOperationFailedException can be thrown
     */
    boolean existsFile(String name) throws GenericFileOperationFailedException;

    /**
     * Renames the file
     *
     * @param from original name
     * @param to   the new name
     * @return true if renamed, false if not
     * @throws GenericFileOperationFailedException can be thrown
     */
    boolean renameFile(String from, String to) throws GenericFileOperationFailedException;

    /**
     * Builds the directory structure. Will test if the
     * folder already exists.
     *
     * @param directory the directory path to build as a relative string name
     * @param absolute wether the directory is an absolute or relative path
     * @return true if build or already exists, false if not possible (could be lack of permissions)
     * @throws GenericFileOperationFailedException can be thrown
     */
    boolean buildDirectory(String directory, boolean absolute) throws GenericFileOperationFailedException;

    /**
     * Retrieves the file
     *
     * @param name     name of the file
     * @param exchange stream to write the content of the file into
     * @param size     the total file size to retrieve, if possible to determine
     * @return true if file has been retrieved, false if not
     * @throws GenericFileOperationFailedException can be thrown
     */
    boolean retrieveFile(String name, Exchange exchange, long size) throws GenericFileOperationFailedException;
    
    /**
     * Releases the resources consumed by a retrieved file
     * 
     * @param exchange exchange with the content of the file
     * @throws GenericFileOperationFailedException can be thrown
     */
    void releaseRetrievedFileResources(Exchange exchange) throws GenericFileOperationFailedException;

    /**
     * Stores the content as a new remote file (upload)
     *
     * @param name     name of new file
     * @param exchange with the content content of the file
     * @param size     the total file size to store, if possible to determine
     * @return true if the file was stored, false if not
     * @throws GenericFileOperationFailedException can be thrown
     */
    boolean storeFile(String name, Exchange exchange, long size) throws GenericFileOperationFailedException;

    /**
     * Gets the current remote directory
     *
     * @return the current directory path
     * @throws GenericFileOperationFailedException can be thrown
     */
    String getCurrentDirectory() throws GenericFileOperationFailedException;

    /**
     * Change the current remote directory
     *
     * @param path the path to change to
     * @throws GenericFileOperationFailedException can be thrown
     */
    void changeCurrentDirectory(String path) throws GenericFileOperationFailedException;

    /**
     * Change the current remote directory to the parent
     *
     * @throws GenericFileOperationFailedException can be thrown
     */
    void changeToParentDirectory() throws GenericFileOperationFailedException;

    /**
     * List the files in the current directory
     *
     * @return a list of backing objects representing the files
     * @throws GenericFileOperationFailedException can be thrown
     */
    List<T> listFiles() throws GenericFileOperationFailedException;

    /**
     * List the files in the given remote directory
     *
     * @param path the remote directory
     * @return a list of backing objects representing the files
     * @throws GenericFileOperationFailedException can be thrown
     */
    List<T> listFiles(String path) throws GenericFileOperationFailedException;
}
