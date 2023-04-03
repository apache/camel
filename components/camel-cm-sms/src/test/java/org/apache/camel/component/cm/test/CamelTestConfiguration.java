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
package org.apache.camel.component.cm.test;

import java.io.FileInputStream;
import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;

/**
 * Builds a SimpleRoute to send a message to CM GW and CM Uri is built based on properties in a file.
 */
public abstract class CamelTestConfiguration extends CamelSpringTestSupport {

    public static final String SIMPLE_ROUTE_ID = "simple-route";

    private String uri;

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new AnnotationConfigApplicationContext();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        loadProperties();

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                log.debug("CM Component is an URI based component\nCM URI: {}", uri);

                // Route definition
                from("direct:sms").to(uri).to("mock:test")
                        .routeId(SIMPLE_ROUTE_ID);
            }
        });

        return context;
    }

    /**
     * Build the URI of the CM Component based on Environmental properties
     */
    private void loadProperties() throws Exception {
        Properties prop = new Properties();
        prop.load(new FileInputStream("src/test/resources/cm-smsgw.properties"));

        final String host = prop.getProperty("cm.url");
        final String productTokenString = prop.getProperty("cm.product-token");
        final String sender = prop.getProperty("cm.default-sender");

        final StringBuilder cmUri = new StringBuilder("cm-sms:" + host)
                .append("?productToken=").append(productTokenString);
        if (sender != null && !sender.isEmpty()) {
            cmUri.append("&defaultFrom=").append(sender);
        }

        // Defaults to false
        final boolean testConnectionOnStartup = Boolean.parseBoolean(
                prop.getProperty("cm.testConnectionOnStartup", "false"));
        if (testConnectionOnStartup) {
            cmUri.append("&testConnectionOnStartup=")
                    .append(testConnectionOnStartup);
        }

        // Defaults to 8
        final int defaultMaxNumberOfParts = Integer
                .parseInt(prop.getProperty("defaultMaxNumberOfParts", "8"));
        cmUri.append("&defaultMaxNumberOfParts=")
                .append(defaultMaxNumberOfParts);

        uri = cmUri.toString();
    }

    public String getUri() {
        return uri;
    }
}
