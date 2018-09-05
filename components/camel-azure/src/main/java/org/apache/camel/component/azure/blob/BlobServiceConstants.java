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

public interface BlobServiceConstants {

    String OPERATION = "operation";
    String BLOB_CLIENT = "AzureBlobClient";
    
    String SERVICE_URI_SEGMENT = ".blob.core.windows.net";
    String BLOB_SERVICE_REQUEST_OPTIONS = "BlobServiceRequestOptions";
    String ACCESS_CONDITION = "BlobAccessCondition";
    String BLOB_REQUEST_OPTIONS = "BlobRequestOptions";
    String OPERATION_CONTEXT = "BlobOperationContext";
    
    String BLOB_LISTING_DETAILS = "BlobListingDetails";
    
    String COMMIT_BLOCK_LIST_LATER = "CommitBlobBlockListLater";
    String APPEND_BLOCK_CREATED = "AppendBlobCreated";
    String PAGE_BLOCK_CREATED = "PageBlobCreated";
    String PAGE_BLOB_RANGE = "PageBlobRange";
    String PAGE_BLOB_SIZE = "PageBlobSize";
}
