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
package org.apache.camel.itest.tx;

import com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionManagerImple;
import org.apache.camel.EndpointInject;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Policy;
import org.apache.camel.spring.spi.SpringTransactionPolicy;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.jta.JtaTransactionManager;

public class JtaRouteSpringTest extends CamelTestSupport {
    @EndpointInject("mock:splitted")
    private MockEndpoint splitted;

    @EndpointInject("direct:requires_new")
    private ProducerTemplate start;

    private Policy policy = new Policy() {
        @Override
        public void beforeWrap(Route route, NamedNode definition) {
            // noop
        }

        @Override
        public Processor wrap(Route route, Processor processor) {
            return processor;
        }
    };

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.getRegistry().bind("PROPAGATION_REQUIRES_NEW", new SpringTransactionPolicy(
                        new JtaTransactionManager(new TransactionManagerImple())));

                from("direct:requires_new")
                        .transacted("PROPAGATION_REQUIRES_NEW")
                        .split(body()).delimiter("_").to("direct:splitted").end()
                        .log("after splitter log which you will never see...")
                        .transform().constant("requires_new");

                from("direct:splitted").to("mock:splitted");
            }
        };
    }

    @Test
    void testTransactedSplit() throws Exception {
        splitted.expectedBodiesReceived("requires", "new");

        start.sendBody("requires_new");

        splitted.assertIsSatisfied();
    }
}
