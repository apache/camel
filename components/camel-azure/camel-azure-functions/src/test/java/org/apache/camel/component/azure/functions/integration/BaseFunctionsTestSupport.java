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
package org.apache.camel.component.azure.functions.integration;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.azure.functions.FunctionsComponent;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.apache.camel.test.infra.core.annotations.ContextFixture;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.apache.camel.test.infra.core.api.ConfigurableRoute;
import org.junit.jupiter.api.extension.RegisterExtension;

import static java.lang.System.getProperty;

/**
 * Base test support class for Azure Functions integration tests.
 */
public abstract class BaseFunctionsTestSupport implements ConfigurableRoute {

    // Property names for Maven -D parameters
    public static final String FUNCTION_APP_PROPERTY = "camel.component.azure-functions.function-app";
    public static final String FUNCTION_NAME_PROPERTY = "camel.component.azure-functions.function-name";
    public static final String FUNCTION_KEY_PROPERTY = "camel.component.azure-functions.function-key";
    public static final String SUBSCRIPTION_ID_PROPERTY = "camel.component.azure-functions.subscription-id";
    public static final String RESOURCE_GROUP_PROPERTY = "camel.component.azure-functions.resource-group";

    // Read properties from system
    protected static final String FUNCTION_APP = getProperty(FUNCTION_APP_PROPERTY);
    protected static final String FUNCTION_NAME = getProperty(FUNCTION_NAME_PROPERTY);
    protected static final String FUNCTION_KEY = getProperty(FUNCTION_KEY_PROPERTY);
    protected static final String SUBSCRIPTION_ID = getProperty(SUBSCRIPTION_ID_PROPERTY);
    protected static final String RESOURCE_GROUP = getProperty(RESOURCE_GROUP_PROPERTY);

    @RegisterExtension
    protected static CamelContextExtension contextExtension = new DefaultCamelContextExtension();

    @ContextFixture
    public void configureComponent(CamelContext context) {
        FunctionsComponent component = new FunctionsComponent(context);
        component.init();

        // Set default configuration if properties are provided
        if (FUNCTION_KEY != null) {
            component.getConfiguration().setFunctionKey(FUNCTION_KEY);
        }
        if (SUBSCRIPTION_ID != null) {
            component.getConfiguration().setSubscriptionId(SUBSCRIPTION_ID);
        }
        if (RESOURCE_GROUP != null) {
            component.getConfiguration().setResourceGroup(RESOURCE_GROUP);
        }

        context.addComponent("azure-functions", component);
    }

    @RouteFixture
    @Override
    public void createRouteBuilder(CamelContext context) throws Exception {
        context.addRoutes(createRouteBuilder());
    }

    protected abstract RouteBuilder createRouteBuilder();
}
