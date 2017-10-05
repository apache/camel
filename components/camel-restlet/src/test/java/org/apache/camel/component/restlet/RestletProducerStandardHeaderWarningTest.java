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
package org.apache.camel.component.restlet;

import java.util.logging.Level;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;
import org.slf4j.bridge.SLF4JBridgeHandler;

/**
 * @version 
 */
public class RestletProducerStandardHeaderWarningTest extends RestletTestSupport {

    @Test
    public void testRestletProducerAuthorizationGet() throws Exception {
        // restlet uses the JUL logger which is a pain to configure/install
        // we should not see WARN logs
        SLF4JBridgeHandler.install();
        java.util.logging.LogManager.getLogManager().getLogger("").setLevel(Level.INFO);

        String out = fluentTemplate.to("direct:start")
            .withHeader("id", 123).withHeader("Authorization", "myuser")
            .request(String.class);
        assertEquals("123;Donald Duck;myuser", out);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("restlet:http://localhost:" + portNum + "/users/{id}/basic").to("log:reply");
                
                from("restlet:http://localhost:" + portNum + "/users/{id}/basic?restletMethods=GET,DELETE")
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            String authorization = exchange.getIn().getHeader("Authorization", String.class);
                            log.info("Authorization header: {}", authorization);
                            String id = exchange.getIn().getHeader("id", String.class);
                            exchange.getOut().setBody(id + ";Donald Duck");
                            if (authorization != null) {
                                String body = exchange.getOut().getBody(String.class) + ";" + authorization;
                                exchange.getOut().setBody(body);
                            }
                        }
                    });
            }
        };
    }
}
