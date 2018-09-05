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
package org.apache.camel.component.salesforce;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.component.salesforce.internal.client.HttpClientHolder;
import org.apache.camel.component.salesforce.internal.client.RestClient;
import org.apache.camel.util.ServiceHelper;

final class SalesforceClientTemplate {

    @FunctionalInterface
    interface RestClientSupplier {
        RestClient restClientWith(CamelContext camelContext, Map<String, Object> parameters) throws Exception;
    }

    @FunctionalInterface
    interface WithClient<T> {
        T invoke(RestClient client);
    }

    static RestClientSupplier restClientSupplier = (camelContext, parameters) -> SalesforceComponent
        .createRestClient(camelContext, parameters);

    private SalesforceClientTemplate() {
        // utility class
    }

    static <T> T invoke(final CamelContext camelContext, final Map<String, Object> parameters,
        final WithClient<T> performer) throws Exception {

        final RestClient client = restClientSupplier.restClientWith(camelContext, parameters);

        return invokeInternal(client, performer);
    }

    static <T> T invoke(final SalesforceComponent component, final Map<String, Object> parameters,
        final WithClient<T> performer) throws Exception {

        final RestClient client = component.createRestClient(parameters);

        return invokeInternal(client, performer);
    }

    private static <T> T invokeInternal(final RestClient client, final WithClient<T> performer) throws Exception {
        // we'll handle HTTP client lifecycle so we'll start here and stop at
        // the end; we won't handle SalesforceSession lifecycle as stoping a
        // SalesforceSession that we borrowed from a configured
        // SalesforceComponent could mean logging out any running endpoints also
        final boolean isHttpClientHolder = client instanceof HttpClientHolder;
        if (isHttpClientHolder) {
            ((HttpClientHolder) client).getHttpClient().start();
        }

        ServiceHelper.startService(client);

        try {
            return performer.invoke(client);
        } finally {
            ServiceHelper.stopService(client);

            if (isHttpClientHolder) {
                ((HttpClientHolder) client).getHttpClient().stop();
            }
        }
    }

}
