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
package org.apache.camel.component.http;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.common.HttpConfiguration;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 */
public class HttpProxyAuthBasicTest extends CamelTestSupport {

    @Test
    public void testProxyAuthBasic() throws Exception {
        HttpClientConfigurer configurer = getMandatoryEndpoint("http://www.google.com/search", HttpEndpoint.class).getHttpClientConfigurer();
        assertNotNull(configurer);

        CompositeHttpConfigurer comp = assertIsInstanceOf(CompositeHttpConfigurer.class, configurer);
        assertEquals(1, comp.getConfigurers().size());

        BasicAuthenticationHttpClientConfigurer basic = assertIsInstanceOf(BasicAuthenticationHttpClientConfigurer.class, comp.getConfigurers().get(0));
        assertTrue(basic.isProxy());
        assertEquals("myUser", basic.getUsername());
        assertEquals("myPassword", basic.getPassword());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                // setup proxy details
                HttpConfiguration config = new HttpConfiguration();
                config.setProxyHost("myProxyHosy");
                config.setProxyPort(1234);
                // proxy requires auth as well
                config.setProxyAuthMethod("Basic");
                config.setProxyAuthUsername("myUser");
                config.setProxyAuthPassword("myPassword");

                HttpComponent http = context.getComponent("http", HttpComponent.class);
                http.setHttpConfiguration(config);

                from("direct:start")
                    .to("http://www.google.com/search");
            }
        };
    }
}
