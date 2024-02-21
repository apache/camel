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
package org.apache.camel.builder.endpoint;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.cron.api.CamelCronConfiguration;
import org.apache.camel.component.cron.api.CamelCronService;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.HasCamelContext;
import org.junit.jupiter.api.Test;

public class CronTest {
    @Test
    public void setUpCronWithEndpointDSL() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.getRegistry().bind("cs", new CronService(context, "cs"));
        context.addRoutes(new EndpointRouteBuilder() {
            @Override
            public void configure() {
                from(cron("tab").schedule("0/1 * * * * ?"))
                        .setBody().constant("x")
                        .to("mock:result");
            }
        });

        MockEndpoint mock = context.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedMinimumMessageCount(1);

        context.start();

        try {
            mock.assertIsSatisfied();
        } finally {
            context.stop();
        }
    }

    @Test
    public void setUpCronWithURI() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.getRegistry().bind("cs", new CronService(context, "cs"));
        context.addRoutes(new EndpointRouteBuilder() {
            @Override
            public void configure() {
                from("cron:tab?schedule=0/1 * * * * ?")
                        .setBody().constant("x")
                        .to("mock:result");
            }
        });

        MockEndpoint mock = context.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedMinimumMessageCount(1);

        context.start();

        try {
            mock.assertIsSatisfied();
        } finally {
            context.stop();
        }
    }

    public static class CronService implements CamelCronService, HasCamelContext {
        private final CamelContext camelContext;
        private final String id;

        public CronService(CamelContext context, String id) {
            this.camelContext = context;
            this.id = id;
        }

        @Override
        public Endpoint createEndpoint(CamelCronConfiguration configuration) throws Exception {
            return camelContext.getEndpoint("timer:tick?period=1&delay=0");
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public CamelContext getCamelContext() {
            return this.camelContext;
        }
    }
}
