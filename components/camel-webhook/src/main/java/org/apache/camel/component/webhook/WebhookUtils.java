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
package org.apache.camel.component.webhook;

import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.spi.RestConsumerFactory;

/**
 * Some utilities for the webhook component.
 */
public final class WebhookUtils {

    private static final String[] DEFAULT_REST_CONSUMER_COMPONENTS = new String[]{"coap", "netty-http", "jetty", "servlet", "undertow"};

    private WebhookUtils() {
    }

    /**
     * Used to locate the most suitable {@code RestConsumerFactory}.
     */
    public static RestConsumerFactory locateRestConsumerFactory(CamelContext context, WebhookConfiguration configuration) {
        RestConsumerFactory factory = null;
        if (configuration.getWebhookComponentName() != null) {
            Object comp = context.getRegistry().lookupByName(configuration.getWebhookComponentName());
            if (comp instanceof RestConsumerFactory) {
                factory = (RestConsumerFactory) comp;
            } else {
                comp = context.getComponent(configuration.getWebhookComponentName());
                if (comp instanceof RestConsumerFactory) {
                    factory = (RestConsumerFactory) comp;
                }
            }

            if (factory == null) {
                if (comp != null) {
                    throw new IllegalArgumentException("Component " + configuration.getWebhookComponentName() + " is not a RestConsumerFactory");
                } else {
                    throw new NoSuchBeanException(configuration.getWebhookComponentName(), RestConsumerFactory.class.getName());
                }
            }
        }

        // try all components
        if (factory == null) {
            for (String name : context.getComponentNames()) {
                Component comp = context.getComponent(name);
                if (comp instanceof RestConsumerFactory) {
                    factory = (RestConsumerFactory) comp;
                    break;
                }
            }
        }

        // lookup in registry
        if (factory == null) {
            Set<RestConsumerFactory> factories = context.getRegistry().findByType(RestConsumerFactory.class);
            if (factories != null && factories.size() == 1) {
                factory = factories.iterator().next();
            }
        }

        // no explicit factory found then try to see if we can find any of the default rest consumer components
        // and there must only be exactly one so we safely can pick this one
        if (factory == null) {
            RestConsumerFactory found = null;
            for (String name : DEFAULT_REST_CONSUMER_COMPONENTS) {
                Object comp = context.getComponent(name, true);
                if (comp instanceof RestConsumerFactory) {
                    if (found == null) {
                        found = (RestConsumerFactory) comp;
                    } else {
                        throw new IllegalArgumentException("Multiple RestConsumerFactory found on classpath. Configure explicit which component to use");
                    }
                }
            }
            if (found != null) {
                factory = found;
            }
        }

        if (factory == null) {
            throw new IllegalStateException("Cannot find RestConsumerFactory in Registry or as a Component to use");
        }
        return factory;
    }

}
