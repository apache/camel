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
package org.apache.camel.component.azure.storage.blob;

public enum BlobOperationsDefinition {
    /**
     * Returns a list of containers in the storage account.
     */
    listBlobContainers,
    /**
     * Creates a new container within a storage account. If a container with the same name already exists,
     * the producer will ignore it.
     */
    createBlobContainer,
    /**
     * Deletes the specified container in the storage account. If the container doesn't exist the operation fails.
     */
    deleteBlobContainer,
    /**
     * Returns a list of blobs in this container, with folder structures flattened.
     */
    listBlobs,
    /**
     * Get the content of the blob, can be restricted to a blob range.
     */
    getBlob,
    /**
     * Delete a blob
     */
    deleteBlob,
    downloadBlobToFile,
    downloadLink,
    uploadBlockBlob,
    stageBlockBlobList,
    commitBlobBlockList,
    getBlobBlockList,
    createAppendBlob,
    commitAppendBlob,
    createPageBlob,
    uploadPageBlob,
    resizePageBlob,
    clearPageBlob,
    getPageBlobRanges,

}
