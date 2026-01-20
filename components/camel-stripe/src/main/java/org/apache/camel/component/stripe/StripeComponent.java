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
package org.apache.camel.component.stripe;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

@Component("stripe")
public class StripeComponent extends DefaultComponent {

    @Metadata(label = "security", secret = true,
              description = "The Stripe API key for authentication")
    private String apiKey;

    @Metadata(label = "advanced",
              description = "Override the default Stripe API base URL (for testing purposes)")
    private String apiBase;

    public StripeComponent() {
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        StripeConfiguration configuration = new StripeConfiguration();
        configuration.setOperation(remaining);

        if (apiKey != null) {
            configuration.setApiKey(apiKey);
        }
        if (apiBase != null) {
            configuration.setApiBase(apiBase);
        }

        StripeEndpoint endpoint = new StripeEndpoint(uri, this, configuration);
        setProperties(endpoint, parameters);

        return endpoint;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiBase() {
        return apiBase;
    }

    public void setApiBase(String apiBase) {
        this.apiBase = apiBase;
    }
}
