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
package org.apache.camel.test.infra.ollama.services;

import org.apache.camel.test.infra.common.services.InfrastructureService;

public interface OllamaInfraService extends InfrastructureService {

    @Deprecated
    String getEndpoint();

    @Deprecated
    String getModel();

    String modelName();

    String baseUrl();

    String baseUrlV1();

    String apiKey();

    default String host() {
        // Extract host from baseUrl (e.g., "http://localhost:11434" -> "localhost")
        String url = baseUrl();
        if (url != null) {
            url = url.replace("http://", "").replace("https://", "");
            int colonIdx = url.indexOf(':');
            if (colonIdx > 0) {
                return url.substring(0, colonIdx);
            }
            return url;
        }
        return "localhost";
    }

    default int port() {
        // Extract port from baseUrl (e.g., "http://localhost:11434" -> 11434)
        String url = baseUrl();
        if (url != null) {
            url = url.replace("http://", "").replace("https://", "");
            int colonIdx = url.indexOf(':');
            if (colonIdx > 0) {
                try {
                    return Integer.parseInt(url.substring(colonIdx + 1));
                } catch (NumberFormatException e) {
                    // Fall through to default
                }
            }
        }
        return 11434;
    }

    default String endpointUri() {
        return String.format("http:%s:%d/api/generate", host(), port());
    }
}
