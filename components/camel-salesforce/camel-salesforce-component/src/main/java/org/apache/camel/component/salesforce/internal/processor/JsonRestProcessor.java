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
package org.apache.camel.component.salesforce.internal.processor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.salesforce.SalesforceEndpoint;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.api.dto.AbstractDTOBase;
import org.apache.camel.component.salesforce.api.dto.CreateSObjectResult;
import org.apache.camel.component.salesforce.api.dto.GlobalObjects;
import org.apache.camel.component.salesforce.api.dto.RestResources;
import org.apache.camel.component.salesforce.api.dto.SObjectBasicInfo;
import org.apache.camel.component.salesforce.api.dto.SObjectDescription;
import org.apache.camel.component.salesforce.api.dto.SearchResult;
import org.apache.camel.component.salesforce.api.dto.Version;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.codehaus.jackson.type.TypeReference;
import org.eclipse.jetty.util.StringUtil;

public class JsonRestProcessor extends AbstractRestProcessor {

    private static final String RESPONSE_TYPE = JsonRestProcessor.class.getName() + ".responseType";

    // it is ok to use a single thread safe ObjectMapper
    private final ObjectMapper objectMapper;

    public JsonRestProcessor(SalesforceEndpoint endpoint) throws SalesforceException {
        super(endpoint);

        this.objectMapper = new ObjectMapper();
        // enable date time support including Joda DateTime
        this.objectMapper.configure(SerializationConfig.Feature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    @Override
    protected void processRequest(Exchange exchange) throws SalesforceException {

        switch (operationName) {
        case GET_VERSIONS:
            // handle in built response types
            exchange.setProperty(RESPONSE_TYPE, new TypeReference<List<Version>>() {
            });
            break;

        case GET_RESOURCES:
            // handle in built response types
            exchange.setProperty(RESPONSE_CLASS, RestResources.class);
            break;

        case GET_GLOBAL_OBJECTS:
            // handle in built response types
            exchange.setProperty(RESPONSE_CLASS, GlobalObjects.class);
            break;

        case GET_BASIC_INFO:
            // handle in built response types
            exchange.setProperty(RESPONSE_CLASS, SObjectBasicInfo.class);
            break;

        case GET_DESCRIPTION:
            // handle in built response types
            exchange.setProperty(RESPONSE_CLASS, SObjectDescription.class);
            break;

        case CREATE_SOBJECT:
            // handle known response type
            exchange.setProperty(RESPONSE_CLASS, CreateSObjectResult.class);
            break;

        case UPSERT_SOBJECT:
            // handle known response type
            exchange.setProperty(RESPONSE_CLASS, CreateSObjectResult.class);
            break;

        case SEARCH:
            // handle known response type
            exchange.setProperty(RESPONSE_TYPE, new TypeReference<List<SearchResult>>() {
            });
            break;

        default:
            // ignore, some operations do not require response class or type
        }
    }

    @Override
    protected InputStream getRequestStream(Exchange exchange) throws SalesforceException {
        try {
            InputStream request;
            Message in = exchange.getIn();
            request = in.getBody(InputStream.class);
            if (request == null) {
                AbstractDTOBase dto = in.getBody(AbstractDTOBase.class);
                if (dto != null) {
                    // marshall the DTO
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    objectMapper.writeValue(out, dto);
                    request = new ByteArrayInputStream(out.toByteArray());
                } else {
                    // if all else fails, get body as String
                    final String body = in.getBody(String.class);
                    if (null == body) {
                        String msg = "Unsupported request message body "
                            + (in.getBody() == null ? null : in.getBody().getClass());
                        throw new SalesforceException(msg, null);
                    } else {
                        request = new ByteArrayInputStream(body.getBytes(StringUtil.__UTF8_CHARSET));
                    }
                }
            }

            return request;

        } catch (IOException e) {
            String msg = "Error marshaling request: " + e.getMessage();
            throw new SalesforceException(msg, e);
        }
    }

    @Override
    protected void processResponse(Exchange exchange, InputStream responseEntity, SalesforceException ex, AsyncCallback callback) {

        // process JSON response for TypeReference
        try {
            // do we need to un-marshal a response
            if (responseEntity != null) {
                Object response = null;
                Class<?> responseClass = exchange.getProperty(RESPONSE_CLASS, Class.class);
                if (responseClass != null) {
                    response = objectMapper.readValue(responseEntity, responseClass);
                } else {
                    TypeReference<?> responseType = exchange.getProperty(RESPONSE_TYPE, TypeReference.class);
                    if (responseType != null) {
                        response = objectMapper.readValue(responseEntity, responseType);
                    } else {
                        // return the response as a stream, for getBlobField
                        response = responseEntity;
                    }
                }
                exchange.getOut().setBody(response);
            } else {
                exchange.setException(ex);
            }
            // copy headers and attachments
            exchange.getOut().getHeaders().putAll(exchange.getIn().getHeaders());
            exchange.getOut().getAttachments().putAll(exchange.getIn().getAttachments());
        } catch (IOException e) {
            String msg = "Error parsing JSON response: " + e.getMessage();
            exchange.setException(new SalesforceException(msg, e));
        } finally {
            // cleanup temporary exchange headers
            exchange.removeProperty(RESPONSE_CLASS);
            exchange.removeProperty(RESPONSE_TYPE);

            // consume response entity
            try {
                if (responseEntity != null) {
                    responseEntity.close();
                }
            } catch (IOException ignored) {
            }

            // notify callback that exchange is done
            callback.done(false);
        }

    }

}
