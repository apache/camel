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
package org.apache.camel.component.twilio;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.twilio.http.TwilioRestClient;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.twilio.internal.TwilioApiCollection;
import org.apache.camel.component.twilio.internal.TwilioApiName;
import org.apache.camel.component.twilio.internal.TwilioConstants;
import org.apache.camel.component.twilio.internal.TwilioPropertiesHelper;
import org.apache.camel.spi.BeanIntrospection;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.component.AbstractApiEndpoint;
import org.apache.camel.support.component.ApiMethod;
import org.apache.camel.support.component.ApiMethodPropertiesHelper;

/**
 * Interact with Twilio REST APIs using Twilio Java SDK.
 */
@UriEndpoint(firstVersion = "2.20.0", scheme = "twilio", title = "Twilio", syntax = "twilio:apiName/methodName",
             apiSyntax = "apiName/methodName",
             category = { Category.API, Category.MESSAGING, Category.CLOUD })
public class TwilioEndpoint extends AbstractApiEndpoint<TwilioApiName, TwilioConfiguration> {

    protected static final Map<String, String> EXECUTOR_METHOD_MAP;

    static {
        Map<String, String> map = new HashMap<>();
        map.put("creator", "create");
        map.put("deleter", "delete");
        map.put("fetcher", "fetch");
        map.put("reader", "read");
        map.put("updater", "update");
        EXECUTOR_METHOD_MAP = Collections.unmodifiableMap(map);
    }

    protected final TwilioComponent component;

    @UriParam
    private TwilioConfiguration configuration;

    public TwilioEndpoint(String uri, TwilioComponent component, TwilioApiName apiName, String methodName,
                          TwilioConfiguration endpointConfiguration) {
        super(uri, component, apiName, methodName, TwilioApiCollection.getCollection().getHelper(apiName),
              endpointConfiguration);
        this.component = component;
        this.configuration = endpointConfiguration;

    }

    @Override
    public Producer createProducer() throws Exception {
        return new TwilioProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        // make sure inBody is not set for consumers
        if (inBody != null) {
            throw new IllegalArgumentException("Option inBody is not supported for consumer endpoint");
        }
        final TwilioConsumer consumer = new TwilioConsumer(this, processor);
        // also set consumer.* properties
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    protected void afterConfigureProperties() {
        // Do nothing
    }

    @Override
    public Object getApiProxy(ApiMethod method, Map<String, Object> args) {
        // Since the proxy methods are static
        return null;
    }

    public Object execute(Object executor, ApiMethod method, Map<String, Object> properties) {
        if (!EXECUTOR_METHOD_MAP.containsKey(method.getName())) {
            throw new IllegalArgumentException("Invalid method name " + method.getName());
        }
        String methodName = EXECUTOR_METHOD_MAP.get(method.getName());
        try {
            BeanIntrospection beanIntrospection = PluginHelper.getBeanIntrospection(getCamelContext());
            for (Map.Entry<String, Object> p : properties.entrySet()) {
                beanIntrospection.setProperty(getCamelContext(), executor, p.getKey(), p.getValue());
            }
            return doExecute(executor, methodName, properties);
        } catch (Exception e) {
            throw new RuntimeCamelException(e);
        }
    }

    protected Object doExecute(Object executor, String methodName, Map<String, Object> properties) throws Exception {
        Method method = executor.getClass().getDeclaredMethod(methodName, TwilioRestClient.class);
        return method.invoke(executor, properties.getOrDefault("client", getRestClient()));
    }

    @Override
    protected ApiMethodPropertiesHelper<TwilioConfiguration> getPropertiesHelper() {
        return TwilioPropertiesHelper.getHelper(getCamelContext());
    }

    @Override
    protected String getThreadProfileName() {
        return TwilioConstants.THREAD_PROFILE_NAME;
    }

    public TwilioRestClient getRestClient() {
        return component.getRestClient();
    }
}
