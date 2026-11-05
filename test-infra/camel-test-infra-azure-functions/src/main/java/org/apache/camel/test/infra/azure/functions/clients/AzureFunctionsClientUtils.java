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
package org.apache.camel.test.infra.azure.functions.clients;

import java.net.URI;
import java.net.URL;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.http.HttpPipelineCallContext;
import com.azure.core.http.HttpPipelineNextPolicy;
import com.azure.core.http.HttpPipelinePosition;
import com.azure.core.http.HttpRequest;
import com.azure.core.http.HttpResponse;
import com.azure.core.http.policy.HttpPipelinePolicy;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.profile.AzureProfile;
import com.azure.resourcemanager.appservice.AppServiceManager;
import org.apache.camel.test.infra.azure.functions.services.AzureFunctionsInfraService;
import reactor.core.publisher.Mono;

public final class AzureFunctionsClientUtils {

    private AzureFunctionsClientUtils() {

    }

    /**
     * Creates an {@link AppServiceManager} for the function apps of the local service, Floci AZ. Floci AZ accepts any
     * bearer token, so the manager authenticates with a static one. It serves Azure Resource Manager over plain HTTP,
     * while the Azure SDK sets a token on HTTPS requests only: the manager is built for {@code https://<host>:<port>/},
     * and a policy sends its requests over HTTP once the token is set.
     *
     * @throws IllegalArgumentException if the Azure Resource Manager endpoint of the service is not plain HTTP, as the
     *                                  one of a remote service
     */
    public static AppServiceManager createLocalAppServiceManager(AzureFunctionsInfraService service) {
        URI endpoint = URI.create(service.resourceManagerEndpoint());
        if (!"http".equals(endpoint.getScheme())) {
            throw new IllegalArgumentException(
                    "Not the Azure Resource Manager endpoint of a local service: " + endpoint);
        }
        String authority = endpoint.getRawAuthority();

        Map<String, String> endpoints = new HashMap<>(AzureEnvironment.AZURE.getEndpoints());
        endpoints.put(AzureEnvironment.Endpoint.RESOURCE_MANAGER.identifier(), "https://" + authority + "/");
        AzureProfile profile = new AzureProfile(null, service.subscriptionId(), new AzureEnvironment(endpoints));

        TokenCredential credential
                = request -> Mono.just(new AccessToken("floci-az", OffsetDateTime.now().plusHours(1)));

        return AppServiceManager.configure()
                .withPolicy(new PlainHttpPolicy(authority))
                .authenticate(credential, profile);
    }

    /**
     * Sends the requests for the given authority over plain HTTP. It runs on each try after the authentication policy,
     * which refuses to set a token on a request that is not HTTPS.
     */
    private static final class PlainHttpPolicy implements HttpPipelinePolicy {
        private final String authority;

        private PlainHttpPolicy(String authority) {
            this.authority = authority;
        }

        @Override
        public Mono<HttpResponse> process(HttpPipelineCallContext context, HttpPipelineNextPolicy next) {
            HttpRequest request = context.getHttpRequest();

            URL url = request.getUrl();
            if ("https".equals(url.getProtocol()) && authority.equalsIgnoreCase(url.getAuthority())) {
                request.setUrl("http" + url.toString().substring("https".length()));
            }

            return next.process();
        }

        @Override
        public HttpPipelinePosition getPipelinePosition() {
            return HttpPipelinePosition.PER_RETRY;
        }
    }
}
