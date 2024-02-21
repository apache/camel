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

import org.apache.camel.CamelContext;
import org.apache.camel.NamedNode;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.spi.RoutePolicyFactory;

/**
 * A RoutePolicyFactory that does not start any route but only registers/unregisters the webhook endpoints when enabled.
 */
public class WebhookRoutePolicyFactory implements RoutePolicyFactory {
    private WebhookAction action;

    public WebhookRoutePolicyFactory() {
    }

    public WebhookRoutePolicyFactory(WebhookAction action) {
        this.action = action;
    }

    public WebhookAction getAction() {
        return action;
    }

    public void setAction(WebhookAction action) {
        this.action = action;
    }

    @Override
    public RoutePolicy createRoutePolicy(CamelContext camelContext, String routeId, NamedNode route) {
        if (action == null) {
            throw new IllegalStateException("A webhook action should be configured");
        }

        return new WebhookRoutePolicy(camelContext, action);
    }
}
