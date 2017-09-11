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
package org.apache.camel.component.servicenow;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.util.ObjectHelper;

public abstract class AbstractServiceNowProcessor implements Processor {

    protected final ServiceNowEndpoint endpoint;
    protected final ServiceNowConfiguration config;
    protected final ObjectMapper mapper;
    protected final ServiceNowClient client;

    // Cache for JavaTypes
    private final JavaTypeCache javaTypeCache;
    private final List<ServiceNowDispatcher> dispatchers;

    protected AbstractServiceNowProcessor(ServiceNowEndpoint endpoint) throws Exception {
        this.javaTypeCache = new JavaTypeCache();
        this.endpoint = endpoint;
        this.config = endpoint.getConfiguration();
        this.mapper = config.getOrCreateMapper();
        this.client = new ServiceNowClient(endpoint.getCamelContext(), config);
        this.dispatchers = new ArrayList<>();
    }

    protected AbstractServiceNowProcessor setBodyAndHeaders(Message message, Class<?> responseModel, Response response) throws Exception {
        if (response != null) {
            setHeaders(message, responseModel, response);
            setBody(message, responseModel, response);
        }

        return this;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        final ServiceNowDispatcher dispatcher = findDispatcher(exchange);

        if (dispatcher != null) {
            dispatcher.process(exchange);
        } else {
            throw new IllegalArgumentException("Unable to process exchange");
        }
    }

    // *********************************
    // Header
    // *********************************

    protected AbstractServiceNowProcessor setHeaders(Message message, Class<?> responseModel, Response response) throws Exception {
        ServiceNowHelper.findOffsets(response, (k, v) -> message.setHeader(k, v));

        String attachmentMeta = response.getHeaderString(ServiceNowConstants.ATTACHMENT_META_HEADER);
        if (ObjectHelper.isNotEmpty(attachmentMeta)) {
            message.setHeader(
                ServiceNowConstants.CONTENT_META,
                mapper.readValue(attachmentMeta, Map.class)
            );
        }

        copyHeader(response, HttpHeaders.CONTENT_TYPE, message, ServiceNowConstants.CONTENT_TYPE);
        copyHeader(response, HttpHeaders.CONTENT_ENCODING, message, ServiceNowConstants.CONTENT_ENCODING);

        if (responseModel != null) {
            message.getHeaders().putIfAbsent(ServiceNowConstants.MODEL, responseModel.getName());
            message.getHeaders().putIfAbsent(ServiceNowConstants.RESPONSE_MODEL, responseModel.getName());
        }

        return this;
    }

    // *********************************
    // Body
    // *********************************

    protected AbstractServiceNowProcessor setBody(Message message, Class<?> model, Response response) throws Exception {
        if (message != null && response != null) {
            if (ObjectHelper.isNotEmpty(response.getHeaderString(HttpHeaders.CONTENT_TYPE))) {

                JsonNode root = response.readEntity(JsonNode.class);
                Map<String, String> responseAttributes = null;

                if (root != null) {
                    Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
                    while (fields.hasNext()) {
                        final Map.Entry<String, JsonNode> entry = fields.next();
                        final String key = entry.getKey();
                        final JsonNode node = entry.getValue();

                        if (ObjectHelper.equal("result", key, true)) {
                            Object body = unwrap(node, model);
                            if (body != null) {
                                message.setHeader(ServiceNowConstants.RESPONSE_TYPE, body.getClass());
                                message.setBody(body);
                            }
                        } else {
                            if (responseAttributes == null) {
                                responseAttributes = new HashMap<>();
                            }

                            responseAttributes.put(key, node.textValue());
                        }
                    }

                    if (responseAttributes != null) {
                        message.setHeader(ServiceNowConstants.RESPONSE_META, responseAttributes);
                    }
                }
            }
        }

        return this;
    }

    protected AbstractServiceNowProcessor validateBody(Message message, Class<?> model) {
        return validateBody(message.getBody(), model);
    }

    protected AbstractServiceNowProcessor validateBody(Object body, Class<?> model) {
        ObjectHelper.notNull(body, "body");

        if (!body.getClass().isAssignableFrom(model)) {
            throw new IllegalArgumentException(
                "Body is not compatible with model (body=" + body.getClass() + ", model=" + model);
        }

        return this;
    }

    protected Object unwrap(JsonNode node, Class<?> model) throws IOException {
        Object result;

        if (node.isArray()) {
            if (model.isInstance(Map.class)) {
                // If the model is a Map, there's no need to use any
                // specific JavaType to instruct Jackson about the
                // expected element type
                result = mapper.treeToValue(node, List.class);
            } else {
                result = mapper.readValue(node.traverse(), javaTypeCache.get(model));
            }
        } else {
            result = mapper.treeToValue(node, model);
        }

        return result;
    }

    // *********************************
    // Helpers
    // *********************************

    protected final void addDispatcher(ServiceNowDispatcher dispatcher) {
        this.dispatchers.add(dispatcher);
    }

    protected final void addDispatcher(String action, Processor processor) {
        addDispatcher(ServiceNowDispatcher.on(action, null, processor));
    }

    protected final void addDispatcher(String action, String subject, Processor processor) {
        addDispatcher(ServiceNowDispatcher.on(action, subject, processor));
    }

    protected final ServiceNowDispatcher findDispatcher(Exchange exchange) {
        ServiceNowDispatcher dispatcher = null;
        for (int i = 0; i < dispatchers.size(); i++) {
            dispatcher = dispatchers.get(i);
            if (dispatcher.match(exchange)) {
                return dispatcher;
            }
        }

        return null;
    }

    // *********************************
    // Helpers
    // *********************************

    protected Object getRequestParamFromHeader(ServiceNowParam sysParam, Message message) {
        return message.getHeader(
            sysParam.getHeader(),
            sysParam.getDefaultValue(config),
            sysParam.getType()
        );
    }

    protected Object getMandatoryRequestParamFromHeader(ServiceNowParam sysParam, Message message) {
        return ObjectHelper.notNull(
            getRequestParamFromHeader(sysParam, message),
            sysParam.getHeader()
        );
    }

    protected void copyHeader(Response from, String fromId, Message to, String toId) {
        Object fromValue = from.getHeaders().getFirst(fromId);
        if (ObjectHelper.isNotEmpty(fromValue)) {
            to.setHeader(toId, fromValue);
        }
    }

    protected Class<?> getRequestModel(Message message) {
        return getRequestModel(message, null);
    }

    protected Class<?> getRequestModel(Message message, String modelName) {
        Class<?> model = null;

        if (message != null) {
            model = message.getHeader(ServiceNowConstants.REQUEST_MODEL, Class.class);
            if (model == null) {
                model = message.getHeader(ServiceNowConstants.MODEL, Class.class);
            }
        }

        return model != null
            ? model
            : ObjectHelper.isEmpty(modelName) ? Map.class : config.getRequestModel(modelName, Map.class);
    }

    protected Class<?> getResponseModel(Message message) {
        return getRequestModel(message, null);
    }

    protected Class<?> getResponseModel(Message message, String modelName) {
        Class<?> model = null;

        if (message != null) {
            model = message.getHeader(ServiceNowConstants.RESPONSE_MODEL, Class.class);
            if (model == null) {
                model = message.getHeader(ServiceNowConstants.MODEL, Class.class);
            }
        }

        return model != null
            ? model
            : ObjectHelper.isEmpty(modelName) ? Map.class : config.getResponseModel(modelName, Map.class);
    }

    protected String getApiVersion(Message message) {
        return message.getHeader(ServiceNowConstants.API_VERSION, config.getApiVersion(), String.class);
    }

    protected String getTableName(Message message) {
        return message.getHeader(ServiceNowParams.PARAM_TABLE_NAME.getHeader(), config.getTable(), String.class);
    }

    protected String getSysID(Message message) {
        return message.getHeader(ServiceNowParams.PARAM_SYS_ID.getHeader(), String.class);
    }

    // *************************************************************************
    // Use ClassValue to lazy create and cache JavaType
    // *************************************************************************

    private class JavaTypeCache extends ClassValue<JavaType> {
        @Override
        protected JavaType computeValue(Class<?> type) {
            return mapper.getTypeFactory().constructCollectionType(List.class, type);
        }
    }
}
