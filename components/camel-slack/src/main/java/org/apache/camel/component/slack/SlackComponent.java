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
package org.apache.camel.component.slack;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.HealthCheckComponent;

@Component("slack")
public class SlackComponent extends HealthCheckComponent {

    @Metadata(label = "webhook")
    private String webhookUrl;

    @Metadata(label = "token")
    private String token;

    public SlackComponent() {
        this(null);
    }

    public SlackComponent(CamelContext context) {
        super(context);
        registerExtension(new SlackComponentVerifierExtension());
    }

    @Override
    protected Endpoint createEndpoint(String uri, String channelName, Map<String, Object> parameters) throws Exception {
        Endpoint endpoint = new SlackEndpoint(uri, channelName, this);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    /**
     * The incoming webhook URL
     */
    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public String getToken() {
        return token;
    }

    /**
     * The token to access Slack. This app needs to have channels:history, groups:history, im:history, mpim:history,
     * channels:read, groups:read, im:read and mpim:read permissions. The User OAuth Token is the kind of token needed.
     */
    public void setToken(String token) {
        this.token = token;
    }
}
