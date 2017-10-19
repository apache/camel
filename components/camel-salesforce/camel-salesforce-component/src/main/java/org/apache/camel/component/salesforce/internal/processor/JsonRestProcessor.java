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
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.salesforce.SalesforceEndpoint;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.api.TypeReferences;
import org.apache.camel.component.salesforce.api.dto.AbstractDTOBase;
import org.apache.camel.component.salesforce.api.dto.CreateSObjectResult;
import org.apache.camel.component.salesforce.api.dto.GlobalObjects;
import org.apache.camel.component.salesforce.api.dto.Limits;
import org.apache.camel.component.salesforce.api.dto.RestResources;
import org.apache.camel.component.salesforce.api.dto.SObjectBasicInfo;
import org.apache.camel.component.salesforce.api.dto.SObjectDescription;
import org.apache.camel.component.salesforce.api.dto.approval.ApprovalResult;
import org.apache.camel.component.salesforce.api.dto.approval.Approvals;
import org.apache.camel.component.salesforce.api.utils.JsonUtils;

public class JsonRestProcessor extends AbstractRestProcessor {

    private static final String RESPONSE_TYPE = JsonRestProcessor.class.getName() + ".responseType";

    // it is ok to use a single thread safe ObjectMapper
    private final ObjectMapper objectMapper;

    public JsonRestProcessor(SalesforceEndpoint endpoint) throws SalesforceException {
        super(endpoint);

        if (endpoint.getConfiguration().getObjectMapper() != null) {
            this.objectMapper = endpoint.getConfiguration().getObjectMapper();
        } else {
            this.objectMapper = JsonUtils.createObjectMapper();
        }
    }

    @Override
    protected void processRequest(Exchange exchange) throws SalesforceException {

        switch (operationName) {
        case GET_VERSIONS:
            // handle in built response types
            exchange.setProperty(RESPONSE_TYPE, TypeReferences.VERSION_LIST_TYPE);
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
            exchange.setProperty(RESPONSE_TYPE, TypeReferences.SEARCH_RESULT_TYPE);
            break;

        case RECENT:
            // handle known response type
            exchange.setProperty(RESPONSE_TYPE, TypeReferences.RECENT_ITEM_LIST_TYPE);
            break;

        case LIMITS:
            // handle known response type
            exchange.setProperty(RESPONSE_CLASS, Limits.class);
            break;

        case APPROVAL:
            // handle known response type
            exchange.setProperty(RESPONSE_CLASS, ApprovalResult.class);
            break;

        case APPROVALS:
            // handle known response type
            exchange.setProperty(RESPONSE_CLASS, Approvals.class);
            break;

        default:
            // ignore, some operations do not require response class or type
        }
    }

    @Override
    protected InputStream getRequestStream(Exchange exchange) throws SalesforceException {
        InputStream request;
        Message in = exchange.getIn();
        request = in.getBody(InputStream.class);
        if (request == null) {
            AbstractDTOBase dto = in.getBody(AbstractDTOBase.class);
            if (dto != null) {
                // marshall the DTO
                request = getRequestStream(dto);
            } else {
                // if all else fails, get body as String
                final String body = in.getBody(String.class);
                if (null == body) {
                    String msg = "Unsupported request message body "
                        + (in.getBody() == null ? null : in.getBody().getClass());
                    throw new SalesforceException(msg, null);
                } else {
                    request = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
                }
            }
        }

        return request;
    }

    @Override
    protected InputStream getRequestStream(final Object object) throws SalesforceException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            objectMapper.writeValue(out, object);
        } catch (IOException e) {
            final String msg = "Error marshaling request: " + e.getMessage();
            throw new SalesforceException(msg, e);
        }

        return new ByteArrayInputStream(out.toByteArray());
    }

    @Override
    protected void processResponse(Exchange exchange, InputStream responseEntity, Map<String, String> headers, 
        SalesforceException ex, AsyncCallback callback) {

        // process JSON response for TypeReference
        try {
            final Message out = exchange.getOut();
            final Message in = exchange.getIn();
            out.copyFromWithNewBody(in, null);
            out.getHeaders().putAll(headers);

            if (ex != null) {
                // if an exception is reported we should not loose it
                if (shouldReport(ex)) {
                    exchange.setException(ex);
                }
            } else if (responseEntity != null) {
                // do we need to un-marshal a response
                final Object response;
                Class<?> responseClass = exchange.getProperty(RESPONSE_CLASS, Class.class);
                if (!rawPayload && responseClass != null) {
                    response = objectMapper.readValue(responseEntity, responseClass);
                } else {
                    TypeReference<?> responseType = exchange.getProperty(RESPONSE_TYPE, TypeReference.class);
                    if (!rawPayload && responseType != null) {
                        response = objectMapper.readValue(responseEntity, responseType);
                    } else {
                        // return the response as a stream, for getBlobField
                        response = responseEntity;
                    }
                }
                out.setBody(response);
            }
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
