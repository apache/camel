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
package org.apache.camel.component.knative.http;

import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enabling SSL is not a request to stop validating certificates. With no truststore and no trust certificates
 * configured the client has to fall back to the JVM default trust anchors, the way the rest of Camel does; accepting
 * every certificate is available, but only when it is asked for.
 */
class KnativeSslClientOptionsTrustTest {

    @Test
    void sslEnabledWithoutATruststoreDoesNotTrustEveryCertificate() throws Exception {
        try (CamelContext context = contextWith("camel.knative.client.ssl.enabled", "true")) {
            KnativeSslClientOptions options = new KnativeSslClientOptions(context);

            assertThat(options.isSslEnabled()).isTrue();
            // Left unset, so Vert.x falls back to the JVM default trust anchors
            assertThat(options.getTrustOptions()).isNull();
        }
    }

    @Test
    void trustAllIsAvailableButHasToBeRequested() throws Exception {
        try (CamelContext context = contextWith(
                "camel.knative.client.ssl.enabled", "true",
                "camel.knative.client.ssl.trust.all", "true")) {
            KnativeSslClientOptions options = new KnativeSslClientOptions(context);

            assertThat(options.getTrustOptions()).isInstanceOf(TrustAllOptions.class);
        }
    }

    private static CamelContext contextWith(String... keyValues) throws Exception {
        Properties properties = new Properties();
        for (int i = 0; i < keyValues.length; i += 2) {
            properties.setProperty(keyValues[i], keyValues[i + 1]);
        }
        CamelContext context = new DefaultCamelContext();
        context.getPropertiesComponent().setInitialProperties(properties);
        context.start();
        return context;
    }
}
