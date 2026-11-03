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

import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.ibm.cloud.cloudant.v1.model.Document;
import com.ibm.cloud.cloudant.v1.model.DocumentResult;
import com.ibm.cloud.sdk.core.http.Response;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CouchDbProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(CouchDbProducer.class);

    private final CouchDbClientWrapper couchClient;

    public CouchDbProducer(CouchDbEndpoint endpoint, CouchDbClientWrapper couchClient) {
        super(endpoint);
        this.couchClient = couchClient;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String method = exchange.getIn().getHeader(CouchDbConstants.HEADER_METHOD, String.class);
        if (ObjectHelper.isEmpty(method) || method.equalsIgnoreCase(CouchDbOperations.UPDATE.toString())) {
            // UPDATE is the method the consumer sets for an insert or an update, so a route from a couchdb consumer
            // saves what it sends instead of doing nothing
            saveDocument(exchange);
        } else if (method.equalsIgnoreCase(CouchDbOperations.DELETE.toString())) {
            deleteDocument(exchange);
        } else if (method.equalsIgnoreCase(CouchDbOperations.GET.toString())) {
            getDocument(exchange);
        } else {
            throw new CouchDbException(
                    "Unsupported " + CouchDbConstants.HEADER_METHOD + " header: " + method
                                       + ". Use GET, DELETE or UPDATE, or no header, which saves the document of the body",
                    exchange);
        }
    }

    private void saveDocument(Exchange exchange) throws Exception {
        JsonObject json = getBodyAsJsonObject(exchange, "save");
        Response<DocumentResult> save = saveJsonObject(json);
        if (save == null) {
            throw new CouchDbException("Could not save document [unknown reason]", exchange);
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("Document saved [_id={}, _rev={}]", save.getResult().getId(), save.getResult().getRev());
        }
        exchange.getIn().setHeader(CouchDbConstants.HEADER_DOC_REV, save.getResult().getRev());
        exchange.getIn().setHeader(CouchDbConstants.HEADER_DOC_ID, save.getResult().getId());
    }

    private void deleteDocument(Exchange exchange) throws Exception {
        JsonObject json = getBodyAsJsonObject(exchange, "delete");
        Response<DocumentResult> delete = deleteJsonObject(json, exchange);
        if (delete == null) {
            throw new CouchDbException("Could not delete document [unknown reason]", exchange);
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("Document deleted [_id={}, _rev={}]", delete.getResult().getId(), delete.getResult().getRev());
        }
        exchange.getIn().setHeader(CouchDbConstants.HEADER_DOC_REV, delete.getResult().getRev());
        exchange.getIn().setHeader(CouchDbConstants.HEADER_DOC_ID, delete.getResult().getId());
    }

    private void getDocument(Exchange exchange) throws CouchDbException {
        // GET reads the document of the id header only: the body is not read, so it can be anything, or none
        String docId = exchange.getIn().getHeader(CouchDbConstants.HEADER_DOC_ID, String.class);
        if (docId == null) {
            throw new CouchDbException("Could not get document, document id is missing", exchange);
        }
        Object response = getElement(docId);

        if (LOG.isTraceEnabled()) {
            LOG.trace("Document retrieved [_id={}]", docId);
        }

        exchange.getIn().setBody(response);
    }

    JsonElement getBodyAsJsonElement(Exchange exchange) throws InvalidPayloadException {
        Object body = exchange.getIn().getMandatoryBody();
        if (body instanceof String bodyString) {
            try {
                return JsonParser.parseString(bodyString);
            } catch (JsonSyntaxException jse) {
                // keep the parse error as the cause; the body is not put in the message, as it may hold secrets
                InvalidPayloadException answer = new InvalidPayloadException(
                        exchange, JsonElement.class, exchange.getIn(), "the body is not valid JSON");
                answer.initCause(jse);
                throw answer;
            }
        } else if (body instanceof JsonElement bodyJsonElement) {
            return bodyJsonElement;
        } else if (body instanceof Document document) {
            return JsonParser.parseString(document.toString());
        } else {
            throw new InvalidPayloadException(
                    exchange, JsonElement.class, exchange.getIn(),
                    "the body must be JSON text, a Gson JsonElement or a Cloudant Document");
        }
    }

    /**
     * The body as the one JSON object of a document. A JSON array, or JSON that is not an object, fails with a message
     * that says so, rather than as an unknown reason.
     */
    private JsonObject getBodyAsJsonObject(Exchange exchange, String action) throws Exception {
        JsonElement json = getBodyAsJsonElement(exchange);
        if (json instanceof JsonObject object) {
            return object;
        }
        if (json instanceof JsonArray) {
            throw new CouchDbException(
                    "The body must be one JSON object to " + action + " a document, but it is a JSON array:"
                                       + " send each document in its own message, for example with the Split EIP",
                    exchange);
        }
        throw new CouchDbException(
                "The body must be one JSON object to " + action + " a document, but it is "
                                   + (json.isJsonNull() ? "empty or JSON null" : "a JSON value that is not an object"),
                exchange);
    }

    private Response<DocumentResult> saveJsonObject(JsonObject obj) {
        Document.Builder documentBuilder = new Document.Builder();
        for (String key : obj.keySet()) {
            if (key.equals("_id")) {
                documentBuilder.id(obj.get(key).getAsString());
            } else {
                documentBuilder.add(key, obj.get(key));
            }
        }

        Document document = documentBuilder.build();
        if (document.getId() == null) {
            document.setId(UUID.randomUUID().toString());
        }
        if (obj.get("_rev") == null) {
            return couchClient.save(document);
        } else {
            return couchClient.update(document);
        }
    }

    private Response<DocumentResult> deleteJsonObject(JsonObject obj, Exchange exchange) throws CouchDbException {
        JsonElement id = obj.get("_id");
        JsonElement rev = obj.get("_rev");
        if (id == null || id.isJsonNull() || rev == null || rev.isJsonNull()) {
            // such as the change a couchdb consumer sends for a deleted document, which is not the document
            throw new CouchDbException("The body must hold the _id and _rev of the document to delete", exchange);
        }
        return couchClient.removeByIdAndRev(id.getAsString(), rev.getAsString());
    }

    private Object getElement(String id) {
        Object response;
        response = couchClient.get(id).getResult();
        return response;
    }
}
