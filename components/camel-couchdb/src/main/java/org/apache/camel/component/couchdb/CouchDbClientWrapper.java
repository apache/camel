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

import java.io.IOException;

import com.google.gson.JsonObject;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.lightcouch.Changes;
import org.lightcouch.CouchDbClient;
import org.lightcouch.CouchDbContext;
import org.lightcouch.CouchDbException;
import org.lightcouch.Response;

/**
 * Necessary to allow mockito to mock this client. Once LightCouch library adds an interface for the client, this class
 * can be removed.
 */
public class CouchDbClientWrapper {

    private final CouchDbClient client;

    public CouchDbClientWrapper(CouchDbClient client) {
        this.client = client;
    }

    public Response update(Object doc) {
        return client.update(doc);
    }

    public Response save(Object doc) {
        return client.save(doc);
    }

    public Response remove(Object doc) {
        return client.remove(doc);
    }

    public Changes changes() {
        return client.changes();
    }

    public Object get(String id) {
        return client.find(id);
    }

    public CouchDbContext context() {
        return client.context();
    }

    /**
     * In CouchDB 2.3.x, the purge_seq field type was changed from number to string. As such, calling
     * {@link org.lightcouch.CouchDbContext#info()} was throwing an exception. This method workarounds the issue by
     * getting the update_seq field while ignoring the purge_seq field.
     *
     * @return The latest update sequence
     */
    public String getLatestUpdateSequence() {
        HttpGet getDbInfoRequest = new HttpGet(client.getDBUri());
        try {
            HttpResponse getDbInfoResponse = client.executeRequest(getDbInfoRequest);
            String dbInfoString = EntityUtils.toString(getDbInfoResponse.getEntity());
            JsonObject dbInfo = client.getGson().fromJson(dbInfoString, JsonObject.class);
            return dbInfo.get("update_seq").getAsString();
        } catch (IOException e) {
            getDbInfoRequest.abort();
            throw new CouchDbException("Error executing request to fetch the latest update sequence. ", e);
        }
    }

}
