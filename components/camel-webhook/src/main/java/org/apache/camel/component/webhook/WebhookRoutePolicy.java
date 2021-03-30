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
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.support.RoutePolicySupport;

class WebhookRoutePolicy extends RoutePolicySupport {
    private final CamelContext context;
    private final WebhookAction action;

    public WebhookRoutePolicy(CamelContext context, WebhookAction action) {
        this.context = context;
        this.action = action;
    }

    @Override
    public void onInit(Route route) {
        super.onInit(route);
        route.setAutoStartup(false);

        if (route.getEndpoint() instanceof WebhookEndpoint) {
            WebhookEndpoint webhook = (WebhookEndpoint) route.getEndpoint();
            if (webhook.getConfiguration() != null && webhook.getConfiguration().isWebhookAutoRegister()) {
                throw new IllegalStateException(
                        "Webhook auto-register is enabled on endpoint " + webhook
                                                + ": it must be disabled when the WebhookRoutePolicy is active");
            }
            executeWebhookAction(webhook.getEndpoint());
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        context.getExecutorServiceManager().newThread("terminator", context::stop).start();
    }

    private void executeWebhookAction(WebhookCapableEndpoint endpoint) {
        switch (this.action) {
            case REGISTER:
                try {
                    endpoint.registerWebhook();
                } catch (Exception ex) {
                    throw new RuntimeCamelException("Unable to register webhook for endpoint " + endpoint, ex);
                }
                return;
            case UNREGISTER:
                try {
                    endpoint.unregisterWebhook();
                } catch (Exception ex) {
                    throw new RuntimeCamelException("Unable to unregister webhook for endpoint " + endpoint, ex);
                }
                return;
            default:
                throw new UnsupportedOperationException("Unsupported webhook action type: " + this.action);
        }
    }
}
