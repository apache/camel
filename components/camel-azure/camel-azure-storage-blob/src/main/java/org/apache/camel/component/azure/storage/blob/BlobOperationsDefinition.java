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

import java.nio.file.FileAlreadyExistsException;

public enum BlobOperationsDefinition {
    // Operations on the service level
    //
    /**
     * Returns a list of containers in the storage account.
     */
    listBlobContainers,

    // Operations on the container level
    //
    /**
     * Creates a new container within a storage account. If a container with the same name already exists, the producer
     * will ignore it.
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

    // Operations on the blob level
    //
    /**
     * Get the content of the blob, can be restricted to a blob range.
     */
    getBlob,
    /**
     * Delete a blob
     */
    deleteBlob,
    /**
     * Downloads the entire blob into a file specified by the path.
     *
     * The file will be created and must not exist, if the file already exists a {@link FileAlreadyExistsException} will
     * be thrown.
     */
    downloadBlobToFile,
    /**
     * Generates the download link for the specified blob using shared access signatures (SAS). This by default only
     * limit to 1hour of allowed access. However, you can override the default expiration duration through the headers.
     */
    downloadLink,
    /**
     * Creates a new block blob, or updates the content of an existing block blob. Updating an existing block blob
     * overwrites any existing metadata on the blob. Partial updates are not supported with PutBlob; the content of the
     * existing blob is overwritten with the new content.
     */
    uploadBlockBlob,
    /**
     * Uploads the specified block to the block blob's "staging area" to be later committed by a call to
     * commitBlobBlockList. However in case header `CamelAzureStorageBlobCommitBlobBlockListLater` is set to false, this
     * will also commit the blocks.
     */
    stageBlockBlobList,
    /**
     * Writes a blob by specifying the list of block IDs that are to make up the blob. In order to be written as part of
     * a blob, a block must have been successfully written to the server in a prior `stageBlockBlobList` operation. You
     * can call `commitBlobBlockList` to update a blob by uploading only those blocks that have changed, then committing
     * the new and existing blocks together. Any blocks not specified in the block list and permanently deleted.
     */
    commitBlobBlockList,
    /**
     * Returns the list of blocks that have been uploaded as part of a block blob using the specified block list filter.
     */
    getBlobBlockList,
    /**
     * Creates a 0-length append blob. Call commitAppendBlo`b operation to append data to an append blob.
     */
    createAppendBlob,
    /**
     * Commits a new block of data to the end of the existing append blob. In case of header
     * `CamelAzureStorageBlobAppendBlobCreated` is set to false, it will attempt to create the appendBlob through
     * internal call to `createAppendBlob` operation.
     */
    commitAppendBlob,
    /**
     * Creates a page blob of the specified length. Call `uploadPageBlob` operation to upload data data to a page blob.
     */
    createPageBlob,
    /**
     * Writes one or more pages to the page blob. The write size must be a multiple of 512. In case of header
     * `CamelAzureStorageBlobPageBlockCreated` is set to false, it will attempt to create the appendBlob through
     * internal call to `createPageBlob` operation.
     */
    uploadPageBlob,
    /**
     * Resizes the page blob to the specified size (which must be a multiple of 512).
     */
    resizePageBlob,
    /**
     * Frees the specified pages from the page blob. The size of the range must be a multiple of 512.
     */
    clearPageBlob,
    /**
     * Returns the list of valid page ranges for a page blob or snapshot of a page blob.
     */
    getPageBlobRanges,
    /**
     * Returns transaction logs of all the changes that occur to the blobs and the blob metadata in your storage
     * account. The change feed provides ordered, guaranteed, durable, immutable, read-only log of these changes.
     */
    getChangeFeed,
    /**
     * Returns transaction logs of all the changes that occur to the blobs and the blob metadata in your storage
     * account. The change feed provides ordered, guaranteed, durable, immutable, read-only log of these changes.
     */
    copyBlob
}
