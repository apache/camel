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
        JsonElement json = getBodyAsJsonElement(exchange);
        String operation = exchange.getIn().getHeader(CouchDbConstants.HEADER_METHOD, String.class);
        if (ObjectHelper.isEmpty(operation)) {
            Response<DocumentResult> save = saveJsonElement(json);
            if (save == null) {
                throw new CouchDbException("Could not save document [unknown reason]", exchange);
            }

            if (LOG.isTraceEnabled()) {
                LOG.trace(
                        "Document saved [_id={}, _rev={}]",
                        save.getResult().getId(),
                        save.getResult().getRev());
            }
            exchange.getIn()
                    .setHeader(CouchDbConstants.HEADER_DOC_REV, save.getResult().getRev());
            exchange.getIn()
                    .setHeader(CouchDbConstants.HEADER_DOC_ID, save.getResult().getId());
        } else {
            if (operation.equalsIgnoreCase(CouchDbOperations.DELETE.toString())) {
                Response<DocumentResult> delete = deleteJsonElement(json);
                if (delete == null) {
                    throw new CouchDbException("Could not delete document [unknown reason]", exchange);
                }

                if (LOG.isTraceEnabled()) {
                    LOG.trace(
                            "Document saved [_id={}, _rev={}]",
                            delete.getResult().getId(),
                            delete.getResult().getRev());
                }
                exchange.getIn()
                        .setHeader(
                                CouchDbConstants.HEADER_DOC_REV,
                                delete.getResult().getRev());
                exchange.getIn()
                        .setHeader(
                                CouchDbConstants.HEADER_DOC_ID,
                                delete.getResult().getId());
            }
            if (operation.equalsIgnoreCase(CouchDbOperations.GET.toString())) {
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
        }
    }

    JsonElement getBodyAsJsonElement(Exchange exchange) throws InvalidPayloadException {
        Object body = exchange.getIn().getMandatoryBody();
        if (body instanceof String) {
            try {
                return new JsonParser().parse((String) body);
            } catch (JsonSyntaxException jse) {
                throw new InvalidPayloadException(exchange, body.getClass());
            }
        } else if (body instanceof JsonElement) {
            return (JsonElement) body;
        } else if (body instanceof Document document) {
            return new JsonParser().parse(document.toString());
        } else {
            throw new InvalidPayloadException(exchange, body != null ? body.getClass() : null);
        }
    }

    private Response<DocumentResult> saveJsonElement(JsonElement json) {
        Response save = null;
        if (json instanceof JsonObject) {
            JsonObject obj = (JsonObject) json;
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
                save = couchClient.save(document);
            } else {
                save = couchClient.update(document);
            }
        }

        return save;
    }

    private Response deleteJsonElement(JsonElement json) {
        Response delete;
        if (json instanceof JsonObject) {
            JsonObject obj = (JsonObject) json;
            delete = couchClient.removeByIdAndRev(
                    obj.get("_id").getAsString(), obj.get("_rev").getAsString());
        } else {
            delete = couchClient.removeByIdAndRev(
                    json.getAsJsonObject().get("_id").getAsString(),
                    json.getAsJsonObject().get("_rev").getAsString());
        }
        return delete;
    }

    private Object getElement(String id) {
        Object response;
        response = couchClient.get(id).getResult();
        return response;
    }
}
