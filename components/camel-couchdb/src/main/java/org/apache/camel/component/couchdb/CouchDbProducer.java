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
package org.apache.camel.component.couchdb;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.lightcouch.Response;

public class CouchDbProducer extends DefaultProducer {

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
            Response save = saveJsonElement(json);
            if (save == null) {
                throw new CouchDbException("Could not save document [unknown reason]", exchange);
            }

            if (log.isTraceEnabled()) {
                log.trace("Document saved [_id={}, _rev={}]", save.getId(), save.getRev());
            }
            exchange.getIn().setHeader(CouchDbConstants.HEADER_DOC_REV, save.getRev());
            exchange.getIn().setHeader(CouchDbConstants.HEADER_DOC_ID, save.getId());
        } else {
            if (operation.equalsIgnoreCase("DELETE")) {
                Response delete = deleteJsonElement(json);
                if (delete == null) {
                    throw new CouchDbException("Could not delete document [unknown reason]", exchange);
                }

                if (log.isTraceEnabled()) {
                    log.trace("Document saved [_id={}, _rev={}]", delete.getId(), delete.getRev());
                }
                exchange.getIn().setHeader(CouchDbConstants.HEADER_DOC_REV, delete.getRev());
                exchange.getIn().setHeader(CouchDbConstants.HEADER_DOC_ID, delete.getId());
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
        } else {
            throw new InvalidPayloadException(exchange, body != null ? body.getClass() : null);
        }
    }

    private Response saveJsonElement(JsonElement json) {
        Response save;
        if (json instanceof JsonObject) {
            JsonObject obj = (JsonObject) json;
            if (obj.get("_rev") == null) {
                save = couchClient.save(json);
            } else {
                save = couchClient.update(json);
            }
        } else {
            save = couchClient.save(json);
        }
        return save;
    }
    
    private Response deleteJsonElement(JsonElement json) {
        Response delete;
        if (json instanceof JsonObject) {
            JsonObject obj = (JsonObject) json;
            delete = couchClient.remove(obj);
        } else {
            delete = couchClient.remove(json);
        }
        return delete;
    }
}
