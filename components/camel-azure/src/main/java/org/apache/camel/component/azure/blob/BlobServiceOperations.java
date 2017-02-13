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
package org.apache.camel.component.azure.blob;

public enum BlobServiceOperations {
    /**
     * Common to all block types
     */
    // Get the content of the blob, can be restricted to a blob range
    getBlob,
    // Delete the blob
    deleteBlob,
    // List the blobs
    listBlobs,
    /*
     * Bloc blob operations
     */
    // Put a block blob content which either creates a new block blob 
    // or overwrites the existing block blob content
    updateBlockBlob,
    
    // Upload a block blob content as a sequence of blob blocks first and then 
    // commit them to a blob. The commit can be executed later with the 
    // commitBlobBlockList operation if a message "CommitBlockListLater" 
    // property is enabled. Individual block blobs can be updated later.
    uploadBlobBlocks,
    
    // Commit a sequence of blob blocks to the block list which was previously
    // uploaded to the blob service with the putBlockBlob operation with the commit
    // being delayed
    commitBlobBlockList,
    
    // Get the block blob list,
    getBlobBlockList,
    
    /*
     * Append blob operations
     */
    // Create an append block. By default if the block already exists then it is not reset.
    // Note the updateAppendBlob will also try to create an append blob first unless
    // a message "AppendBlobCreated" property is enabled
    createAppendBlob,
    
    // Create an append block unless a message "AppendBlobCreated" property is enabled and no 
    // the identically named block already exists and append the new content to this blob.
    updateAppendBlob,
    
    /**
     * Page Block operations
     */
    // Create a page block. By default if the block already exists then it is not reset.
    // Note the updatePageBlob will also try to create a page blob first unless
    // a message "PageBlobCreated" property is enabled
    createPageBlob,
    
    // Create a page block unless a message "PageBlobCreated" property is enabled and no 
    // the identically named block already exists and set the content of this blob.
    updatePageBlob,
    
    // Resize the page blob
    resizePageBlob,
    
    // Clear the page blob
    clearPageBlob,
    
    // Get the page blob page ranges
    getPageBlobRanges
    
}
