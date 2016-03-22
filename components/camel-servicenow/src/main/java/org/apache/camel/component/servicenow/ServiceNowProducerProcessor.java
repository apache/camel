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

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.util.ObjectHelper;

public abstract class ServiceNowProducerProcessor<T> implements Processor {

    protected final ServiceNowEndpoint endpoint;
    protected final ServiceNowConfiguration config;
    protected final T client;
    protected final ObjectMapper mapper;
    
    // Cache for JavaTypes
    private final JavaTypeCache javaTypeCache;

    protected ServiceNowProducerProcessor(ServiceNowEndpoint endpoint, Class<T> type) throws Exception {
        this.javaTypeCache = new JavaTypeCache();
        this.endpoint = endpoint;
        this.config = endpoint.getConfiguration();
        this.client = endpoint.createClient(type);
        this.mapper = ObjectHelper.notNull(config.getMapper(), "mapper");
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        final Message in = exchange.getIn();
        final String tableName = in.getHeader(ServiceNowConstants.TABLE, config.getTable(), String.class);
        final Class<?> model = in.getHeader(ServiceNowConstants.MODEL, config.getModel(tableName, Map.class), Class.class);
        final String action = in.getHeader(ServiceNowConstants.ACTION, String.class);
        final String sysId = in.getHeader(ServiceNowConstants.SYSPARM_ID, String.class);

        doProcess(
            exchange,
            ObjectHelper.notNull(model, "model"),
            ObjectHelper.notNull(action, "action"),
            ObjectHelper.notNull(tableName, "tableName"),
            sysId);
    }

    protected abstract void doProcess(
        Exchange exchange,
        Class<?> model,
        String action,
        String tableName,
        String sysId) throws Exception;


    protected ServiceNowProducerProcessor<T> validateBody(Message message, Class<?> model) {
        return validateBody(message.getBody(), model);
    }

    protected ServiceNowProducerProcessor<T> validateBody(Object body, Class<?> model) {
        ObjectHelper.notNull(body, "body");

        if (!body.getClass().isAssignableFrom(model)) {
            throw new IllegalArgumentException(
                "Body is not compatible with model (body=" + body.getClass() + ", model=" + model);
        }

        return this;
    }

    protected ServiceNowProducerProcessor<T> setBody(Message message, Class<?> model, JsonNode answer) throws Exception {
        message.setBody(unwrap(answer, model));

        return this;
    }

    protected Object unwrap(JsonNode answer, Class<?> model) throws Exception {
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

    // *************************************************************************
    // Use ClassValue to lazy create and cache JavaType
    // *************************************************************************

    private class JavaTypeCache extends ClassValue<JavaType> {
        @Override
        protected JavaType computeValue(Class<?> type) {
            return mapper.getTypeFactory().constructCollectionType(List.class, type);
        }
    }

    // *************************************************************************
    // To be replaced with:
    //
    //     java.util.function.Function<ServiceNowEndpoint, Processor>
    //
    // Once Camel will be Java 8 ready
    // *************************************************************************

    public interface Supplier {
        Processor get(ServiceNowEndpoint endpoint) throws Exception;
    }
}
