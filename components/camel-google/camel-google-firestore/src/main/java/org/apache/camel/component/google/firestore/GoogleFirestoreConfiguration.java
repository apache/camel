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
import org.apache.camel.component.google.common.GoogleCommonConfiguration;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

/**
 * Configuration for the Google Firestore component.
 */
@UriParams
public class GoogleFirestoreConfiguration implements Cloneable, GoogleCommonConfiguration {

    @UriPath(label = "common",
             description = "The name of the collection. The consumer reads the documents of this collection, and the"
                           + " CamelGoogleFirestoreCollectionName header replaces it for the producer.")
    @Metadata(required = true)
    private String collectionName;

    @UriParam(label = "common",
              description = "The ID of the Google Cloud project. If not set, the Firestore client takes it from the service"
                            + " account key, or from the environment, such as the GOOGLE_CLOUD_PROJECT environment variable.")
    private String projectId;

    @UriParam(label = "common",
              description = "The Firestore database ID. If not specified, the default database '(default)' will be used.")
    private String databaseId;

    @UriParam(label = "common",
              description = "The service account key file in JSON format that the Firestore client uses as credentials. It is"
                            + " loaded from the classpath by default, or from another location with the prefix file: or http:."
                            + " If not set, the client uses Application Default Credentials.")
    private String serviceAccountKey;

    @UriParam(label = "producer",
              description = "The operation that the producer performs. If not set, the producer performs setDocument. The"
                            + " CamelGoogleFirestoreOperation header replaces this option.",
              enums = "setDocument,getDocumentById,updateDocument,deleteDocument,queryCollection,listDocuments,listCollections,createDocument")
    private GoogleFirestoreOperations operation;

    @UriParam(label = "producer",
              description = "The ID of the document for setDocument, getDocumentById, updateDocument and deleteDocument,"
                            + " which fail without one. For listCollections it is the document whose subcollections are"
                            + " listed, and if not set the root collections are listed. The CamelGoogleFirestoreDocumentId"
                            + " header replaces this option.")
    private String documentId;

    @UriParam(description = "To use a custom com.google.cloud.firestore.Firestore client. When this option is set or"
                            + " autowired, the projectId, databaseId and serviceAccountKey options are not used. The Camel"
                            + " endpoint does not close a client that is set or autowired.")
    @Metadata(autowired = true)
    private Firestore firestoreClient;

    @UriParam(label = "consumer", defaultValue = "false",
              description = "Whether the consumer listens for realtime changes of the collection with a snapshot listener."
                            + " The first snapshot reports every existing document as ADDED, and each poll routes the changes"
                            + " buffered since the previous poll. If false, each poll reads all documents of the collection,"
                            + " so a document is consumed again in every poll.")
    private boolean realtimeUpdates;

    @UriParam(label = "consumer,advanced", defaultValue = "0",
              description = "Maximum number of realtime document changes buffered between two polls. The changes reported by"
                            + " the snapshot listener are buffered until the next poll picks them up, so a collection"
                            + " changing faster than the route consumes it makes that buffer grow. When the buffer is full"
                            + " the oldest buffered change is discarded and a warning is logged. Use 0 for an unbounded"
                            + " buffer. Only used when realtimeUpdates is enabled.")
    private int maxPendingChanges;

    public String getCollectionName() {
        return collectionName;
    }

    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getDatabaseId() {
        return databaseId;
    }

    public void setDatabaseId(String databaseId) {
        this.databaseId = databaseId;
    }

    @Override
    public String getServiceAccountKey() {
        return serviceAccountKey;
    }

    public void setServiceAccountKey(String serviceAccountKey) {
        this.serviceAccountKey = serviceAccountKey;
    }

    public GoogleFirestoreOperations getOperation() {
        return operation;
    }

    public void setOperation(GoogleFirestoreOperations operation) {
        this.operation = operation;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public Firestore getFirestoreClient() {
        return firestoreClient;
    }

    public void setFirestoreClient(Firestore firestoreClient) {
        this.firestoreClient = firestoreClient;
    }

    public boolean isRealtimeUpdates() {
        return realtimeUpdates;
    }

    public void setRealtimeUpdates(boolean realtimeUpdates) {
        this.realtimeUpdates = realtimeUpdates;
    }

    public int getMaxPendingChanges() {
        return maxPendingChanges;
    }

    public void setMaxPendingChanges(int maxPendingChanges) {
        this.maxPendingChanges = maxPendingChanges;
    }

    public GoogleFirestoreConfiguration copy() {
        try {
            return (GoogleFirestoreConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
