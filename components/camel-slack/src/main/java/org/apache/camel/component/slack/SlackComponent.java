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
package org.apache.camel.component.slack;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;

public class SlackComponent extends UriEndpointComponent {

    private String webhookUrl;

    public SlackComponent() {
        super(SlackEndpoint.class);
    }

    /**
     * Create a slack endpoint
     *
     * @param uri         the full URI of the endpoint
     * @param channelName the channel or username that the message should be sent to
     * @param parameters  the optional parameters passed in
     * @return the camel endpoint
     */
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
}
