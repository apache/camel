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
package org.apache.camel.component.ibm.watson.language.integration;

import org.apache.camel.test.junit6.CamelTestSupport;

/**
 * Base class for Watson Natural Language Understanding integration tests.
 */
public abstract class WatsonLanguageTestSupport extends CamelTestSupport {

    protected static String apiKey;
    protected static String serviceUrl;

    static {
        apiKey = System.getProperty("camel.ibm.watson.apiKey");
        serviceUrl = System.getProperty("camel.ibm.watson.serviceUrl");
    }

    protected String buildEndpointUri() {
        return buildEndpointUri(null);
    }

    protected String buildEndpointUri(String operation) {
        StringBuilder uri = new StringBuilder("ibm-watson-language://default");
        uri.append("?apiKey=RAW(").append(apiKey).append(")");

        if (serviceUrl != null && !serviceUrl.isEmpty()) {
            uri.append("&serviceUrl=").append(serviceUrl);
        }

        if (operation != null && !operation.isEmpty()) {
            uri.append("&operation=").append(operation);
        }

        return uri.toString();
    }
}
