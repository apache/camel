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
package org.apache.camel.component.google.functions;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class GoogleCloudFunctionsConfiguration implements Cloneable {

    @UriPath(label = "common", description = "Function name")
    @Metadata(required = true)
    private String functionName;

    @UriParam(label = "common", description = "Service account key to authenticate an application as a service account")
    private String serviceAccountKey;

    /*
    @UriParam(label = "producer",
              enums = "copyObject,listObjects,deleteObject,deleteBucket,listBuckets,getObject,createDownloadLink")
    private GoogleCloudStorageOperations operation;
    
    @UriParam(label = "producer", description = "The Object name inside the bucket")
    private String objectName;
    
    @UriParam(label = "common", defaultValue = "US-EAST1",
              description = "The Cloud Storage location to use when creating the new buckets")
    private String storageLocation = "US-EAST1";
    
    @UriParam(label = "common", defaultValue = "STANDARD",
              description = "The Cloud Storage class to use when creating the new buckets")
    private StorageClass storageClass = StorageClass.STANDARD;
    
    @UriParam(label = "common", defaultValue = "true")
    private boolean autoCreateBucket = true;
    
    @UriParam(label = "consumer")
    private boolean moveAfterRead;
    
    @UriParam(label = "consumer")
    private String destinationBucket;
    
    @UriParam(label = "consumer", defaultValue = "true")
    private boolean deleteAfterRead = true;
    
    @UriParam(label = "consumer", defaultValue = "true")
    private boolean includeBody = true;
    
    @UriParam(label = "consumer", defaultValue = "true")
    private boolean includeFolders = true;
    
    @UriParam
    @Metadata(autowired = true)
    private Storage storageClient;
    
    public String getBucketName() {
        return this.bucketName;
    }
    */

    public String getFunctionName() {
        return functionName;
    }

    /**
     * Set the function name
     * 
     * @param functionName
     */
    public void setFunctionName(String functionName) {
        this.functionName = functionName;
    }

    public String getServiceAccountKey() {
        return serviceAccountKey;
    }

    /**
     * Service account key
     * 
     * @param serviceAccountKey
     */
    public void setServiceAccountKey(String serviceAccountKey) {
        this.serviceAccountKey = serviceAccountKey;
    }

    public GoogleCloudFunctionsConfiguration copy() {
        try {
            return (GoogleCloudFunctionsConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    @Override
    public String toString() {
        return "GoogleCloudFunctionsConfiguration [functionName=" + functionName + ", serviceAccountKey="
               + serviceAccountKey + "]";
    }

}
