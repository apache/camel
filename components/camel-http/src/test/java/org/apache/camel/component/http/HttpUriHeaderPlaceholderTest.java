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
package org.apache.camel.component.http;

import java.util.Properties;

import org.apache.camel.Exchange;
import org.apache.camel.http.common.HttpHelper;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property placeholders are resolved at build time on the endpoint uri written in the route, never on the
 * {@code CamelHttpUri} header, which carries message content. See CAMEL-24282 / CAMEL-24418.
 */
class HttpUriHeaderPlaceholderTest {

    @Test
    void placeholderInHttpUriHeaderIsNotResolved() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            Properties prop = new Properties();
            prop.setProperty("secretValue", "s3cr3t");
            context.getPropertiesComponent().setInitialProperties(prop);
            context.start();

            HttpEndpoint endpoint = context.getEndpoint("http://localhost/base", HttpEndpoint.class);
            Exchange exchange = new DefaultExchange(context);
            exchange.getIn().setHeader(Exchange.HTTP_URI, "http://localhost/api?k={{secretValue}}");

            // the token survives as a literal (percent-encoded by UnsafeUriCharactersEncoder, as any other
            // unsafe character in a header-supplied uri would be) and is never expanded
            assertThat(HttpHelper.createURL(exchange, endpoint))
                    .isEqualTo("http://localhost/api?k=%7B%7BsecretValue%7D%7D")
                    .doesNotContain("s3cr3t");
        }
    }

    @Test
    void placeholderInEndpointUriIsResolvedAtBuildTime() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            Properties prop = new Properties();
            prop.setProperty("basePath", "resolved");
            context.getPropertiesComponent().setInitialProperties(prop);
            context.start();

            // control: a placeholder written in the route's endpoint uri is still resolved, as before
            HttpEndpoint endpoint = context.getEndpoint("http://localhost/{{basePath}}", HttpEndpoint.class);
            Exchange exchange = new DefaultExchange(context);

            assertThat(HttpHelper.createURL(exchange, endpoint)).isEqualTo("http://localhost/resolved");
        }
    }
}
