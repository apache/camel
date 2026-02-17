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
package org.apache.camel.component.google.firestore;

import com.google.cloud.firestore.Firestore;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

/**
 * Configuration for the Google Firestore component.
 */
@UriParams
public class GoogleFirestoreConfiguration implements Cloneable {

    @UriPath(label = "common", description = "The collection name to use")
    @Metadata(required = true)
    private String collectionName;

    @UriParam(label = "common",
              description = "The Google Cloud project ID. If not specified, it will be determined from the service account key or environment.")
    private String projectId;

    @UriParam(label = "common",
              description = "The Firestore database ID. If not specified, the default database '(default)' will be used.")
    private String databaseId;

    @UriParam(label = "common",
              description = "The Service account key that can be used as credentials for the Firestore client. It can be loaded by default from "
                            + "classpath, but you can prefix with classpath:, file:, or http: to load the resource from different systems.")
    private String serviceAccountKey;

    @UriParam(label = "producer",
              enums = "setDocument,getDocumentById,updateDocument,deleteDocument,queryCollection,listDocuments,listCollections,createDocument")
    private GoogleFirestoreOperations operation;

    @UriParam(label = "producer", description = "The document ID to use for document-specific operations")
    private String documentId;

    @UriParam
    @Metadata(autowired = true)
    private Firestore firestoreClient;

    @UriParam(label = "consumer", defaultValue = "false",
              description = "When true, the consumer will listen for real-time updates on the collection")
    private boolean realtimeUpdates;

    public String getCollectionName() {
        return collectionName;
    }

    /**
     * The collection name to use for Firestore operations.
     */
    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }

    public String getProjectId() {
        return projectId;
    }

    /**
     * The Google Cloud project ID. If not specified, it will be determined from the service account key or environment.
     */
    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getDatabaseId() {
        return databaseId;
    }

    /**
     * The Firestore database ID. If not specified, the default database '(default)' will be used.
     */
    public void setDatabaseId(String databaseId) {
        this.databaseId = databaseId;
    }

    public String getServiceAccountKey() {
        return serviceAccountKey;
    }

    /**
     * The Service account key that can be used as credentials for the Firestore client. It can be loaded by default
     * from classpath, but you can prefix with "classpath:", "file:", or "http:" to load the resource from different
     * systems.
     */
    public void setServiceAccountKey(String serviceAccountKey) {
        this.serviceAccountKey = serviceAccountKey;
    }

    public GoogleFirestoreOperations getOperation() {
        return operation;
    }

    /**
     * Set the operation for the producer.
     */
    public void setOperation(GoogleFirestoreOperations operation) {
        this.operation = operation;
    }

    public String getDocumentId() {
        return documentId;
    }

    /**
     * The document ID to use for document-specific operations.
     */
    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public Firestore getFirestoreClient() {
        return firestoreClient;
    }

    /**
     * The Firestore client to use for operations.
     */
    public void setFirestoreClient(Firestore firestoreClient) {
        this.firestoreClient = firestoreClient;
    }

    public boolean isRealtimeUpdates() {
        return realtimeUpdates;
    }

    /**
     * When true, the consumer will listen for real-time updates on the collection instead of polling.
     */
    public void setRealtimeUpdates(boolean realtimeUpdates) {
        this.realtimeUpdates = realtimeUpdates;
    }

    public GoogleFirestoreConfiguration copy() {
        try {
            return (GoogleFirestoreConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
