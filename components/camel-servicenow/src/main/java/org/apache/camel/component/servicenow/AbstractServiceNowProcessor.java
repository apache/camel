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
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;

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
        this.mapper = ObjectHelper.notNull(config.getMapper(), "mapper");
        this.client = new ServiceNowClient(config);
        this.dispatchers = new ArrayList<>();
    }

    protected AbstractServiceNowProcessor setBodyAndHeaders(Message message, Class<?> model, Response response) throws Exception {
        if (response != null) {
            setHeaders(message, model, response);
            setBody(message, model, response);
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


    protected AbstractServiceNowProcessor setHeaders(Message message, Class<?> model, Response response) throws Exception {
        List<String> links = response.getStringHeaders().get(HttpHeaders.LINK);
        if (links != null) {
            for (String link : links) {
                String[] parts = link.split(";");
                if (parts.length != 2) {
                    continue;
                }

                // Sanitize parts
                String uri = ObjectHelper.between(parts[0], "<", ">");
                String rel = StringHelper.removeQuotes(ObjectHelper.after(parts[1], "="));

                Map<String, Object> query = URISupport.parseQuery(uri);
                Object offset = query.get(ServiceNowParams.SYSPARM_OFFSET.getId());

                if (offset != null) {
                    switch (rel) {
                    case ServiceNowConstants.LINK_FIRST:
                        message.setHeader(ServiceNowConstants.OFFSET_FIRST, offset);
                        break;
                    case ServiceNowConstants.LINK_LAST:
                        message.setHeader(ServiceNowConstants.OFFSET_LAST, offset);
                        break;
                    case ServiceNowConstants.LINK_NEXT:
                        message.setHeader(ServiceNowConstants.OFFSET_NEXT, offset);
                        break;
                    case ServiceNowConstants.LINK_PREV:
                        message.setHeader(ServiceNowConstants.OFFSET_PREV, offset);
                        break;
                    default:
                        break;
                    }
                }
            }
        }

        String attachmentMeta = response.getHeaderString(ServiceNowConstants.ATTACHMENT_META_HEADER);
        if (ObjectHelper.isNotEmpty(attachmentMeta)) {
            message.setHeader(
                ServiceNowConstants.CONTENT_META,
                mapper.readValue(attachmentMeta, Map.class)
            );
        }

        copyHeader(response, HttpHeaders.CONTENT_TYPE, message, ServiceNowConstants.CONTENT_TYPE);
        copyHeader(response, HttpHeaders.CONTENT_ENCODING, message, ServiceNowConstants.CONTENT_ENCODING);

        if (model != null) {
            message.getHeaders().putIfAbsent(ServiceNowConstants.MODEL, model.getName());
        }

        return this;
    }

    // *********************************
    // Body
    // *********************************

    protected AbstractServiceNowProcessor setBody(Message message, Class<?> model, Response response) throws Exception {
        if (message != null && response != null) {
            if (ObjectHelper.isNotEmpty(response.getHeaderString(HttpHeaders.CONTENT_TYPE))) {
                JsonNode node = response.readEntity(JsonNode.class);
                Object body = unwrap(node, model);

                if (body != null) {
                    message.setBody(body);
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

    protected Object unwrap(JsonNode answer, Class<?> model) throws IOException {
        Object result = null;

        if (answer != null) {
            JsonNode node = answer.get("result");
            if (node != null) {
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
            }
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

    protected Class<?> getModel(Message message) {
        return getModel(message, null);
    }

    protected Class<?> getModel(Message message, String modelName) {
        return message.getHeader(
            ServiceNowConstants.MODEL,
            ObjectHelper.isEmpty(modelName) ? Map.class : config.getModel(modelName, Map.class),
            Class.class);
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
