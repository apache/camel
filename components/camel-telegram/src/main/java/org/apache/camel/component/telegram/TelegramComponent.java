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
package org.apache.camel.component.telegram;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.ObjectHelper;

/**
 * The Camel component for Telegram.
 */
public class TelegramComponent extends UriEndpointComponent {

    @Metadata(label = "security")
    private String authorizationToken;

    public TelegramComponent() {
        super(TelegramEndpoint.class);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        TelegramConfiguration configuration = new TelegramConfiguration();
        setProperties(configuration, parameters);
        if (ObjectHelper.isNotEmpty(remaining)) {
            configuration.updatePathConfig(remaining, this.getAuthorizationToken());
        }

        if (TelegramConfiguration.ENDPOINT_TYPE_BOTS.equals(configuration.getType())) {
            return new TelegramEndpoint(uri, this, configuration);
        }

        throw new IllegalArgumentException("Unsupported endpoint type for uri " + uri + ", remaining " + remaining);
    }

    public String getAuthorizationToken() {
        return authorizationToken;
    }

    /**
     * The default Telegram authorization token to be used when the information is not provided in the endpoints.
     */
    public void setAuthorizationToken(String authorizationToken) {
        this.authorizationToken = authorizationToken;
    }

}
