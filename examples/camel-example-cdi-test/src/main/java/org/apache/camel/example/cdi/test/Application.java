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
package org.apache.camel.example.cdi.test;

import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.inject.Named;

import org.apache.camel.Body;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cdi.ContextName;
import org.apache.camel.cdi.Uri;
import org.apache.camel.management.event.CamelContextStartedEvent;
import org.apache.camel.management.event.CamelContextStoppingEvent;

/**
 * Our CDI Camel application
 */
public class Application {

    @ContextName("camel-test-cdi")
    static class Hello extends RouteBuilder {

        @Override
        public void configure() {
            from("direct:message")
                .routeId("route")
                .log("${body} from ${camelContext.name}");

            from("direct:in").routeId("in»out").bean("bean").to("direct:out");
        }
    }

    @Inject
    @Uri("direct:message")
    ProducerTemplate producer;

    void hello(@Observes CamelContextStartedEvent event) {
        producer.sendBody("Hello");
    }

    void bye(@Observes CamelContextStoppingEvent event) {
        producer.sendBody("Bye");
    }

    @Named("bean")
    public static class Bean {

        public String process(@Body String body) {
            return body;
        }
    }
}
