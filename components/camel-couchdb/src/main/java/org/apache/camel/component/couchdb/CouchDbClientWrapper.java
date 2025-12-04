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

package org.apache.camel.component.couchdb;

import com.ibm.cloud.cloudant.v1.Cloudant;
import com.ibm.cloud.cloudant.v1.model.*;
import com.ibm.cloud.sdk.core.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Necessary to allow mockito to mock this client.
 */
public class CouchDbClientWrapper {
    private static final Logger log = LoggerFactory.getLogger(CouchDbEndpoint.class);

    private final Cloudant client;
    private final String dbName;

    public CouchDbClientWrapper(Cloudant client, String dbName, boolean createDatabase) {
        this.client = client;
        this.dbName = dbName;

        initDatabase(createDatabase);
    }

    public void initDatabase(boolean createDatabase) {
        if (createDatabase) {
            boolean alreadyCreated = false;
            for (String db : client.getAllDbs().execute().getResult()) {
                if (db.equals(dbName)) {
                    alreadyCreated = true;
                    break;
                }
            }

            if (!alreadyCreated) {
                PutDatabaseOptions putDatabaseOptions =
                        new PutDatabaseOptions.Builder().db(dbName).build();

                client.putDatabase(putDatabaseOptions).execute();
            }

            log.debug("Database {} created", dbName);
        }
    }

    public Response<DocumentResult> update(Document doc) {
        PostDocumentOptions postDocumentOptions =
                new PostDocumentOptions.Builder().document(doc).db(dbName).build();

        return client.postDocument(postDocumentOptions).execute();
    }

    public Response save(Document doc) {
        PutDocumentOptions putDocumentOptions = new PutDocumentOptions.Builder()
                .document(doc)
                .docId(doc.getId())
                .db(dbName)
                .build();

        return client.putDocument(putDocumentOptions).execute();
    }

    public Response removeByIdAndRev(String id, String rev) {
        DeleteDocumentOptions deleteDocumentOptions = new DeleteDocumentOptions.Builder()
                .docId(id)
                .rev(rev)
                .db(dbName)
                .build();

        return client.deleteDocument(deleteDocumentOptions).execute();
    }

    public Response<ChangesResult> pollChanges(String style, String since, long heartBeat, long maxMessagesPerPoll) {
        PostChangesOptions postChangesOptions = new PostChangesOptions.Builder()
                .db(dbName)
                .since(since)
                .limit(maxMessagesPerPoll)
                .build();

        return client.postChanges(postChangesOptions).execute();
    }

    public Response get(String id) {
        GetDocumentOptions getDocumentOptions =
                new GetDocumentOptions.Builder().docId(id).db(dbName).build();

        return client.getDocument(getDocumentOptions).execute();
    }

    /**
     * In CouchDB 2.3.x, the purge_seq field type was changed from number to string. As such, calling
     * {@link CouchDbContext#info()} was throwing an exception. This method workarounds the issue by getting the
     * update_seq field while ignoring the purge_seq field.
     *
     * @return The latest update sequence
     */
    public String getLatestUpdateSequence() {
        GetDatabaseInformationOptions getDatabaseInformationOptions =
                new GetDatabaseInformationOptions.Builder().db(dbName).build();

        return client.getDatabaseInformation(getDatabaseInformationOptions)
                .execute()
                .getResult()
                .getUpdateSeq();
    }
}
