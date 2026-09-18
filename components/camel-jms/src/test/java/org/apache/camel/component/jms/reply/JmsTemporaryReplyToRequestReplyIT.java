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
package org.apache.camel.component.jms.reply;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.test.infra.artemis.common.ConnectionFactoryHelper;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisServiceFactory;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;
import static org.assertj.core.api.Assertions.assertThat;

class JmsTemporaryReplyToRequestReplyIT {

    private static final String REQUEST_QUEUE = "JmsTemporaryReplyToRequestReplyIT.request";

    @RegisterExtension
    static ArtemisService service = ArtemisServiceFactory.createVMService();

    @RegisterExtension
    static DefaultCamelContextExtension contextExtension = new DefaultCamelContextExtension();

    private ProducerTemplate template;

    @BeforeEach
    void setUp() throws Exception {
        CamelContext context = contextExtension.getContext();
        JmsComponent component = jmsComponentAutoAcknowledge(ConnectionFactoryHelper.createConnectionFactory(service));
        context.addComponent("jms", component);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("jms:queue:" + REQUEST_QUEUE).routeId("server")
                        .transform(simple("echo:${body}"));
            }
        });
        template = contextExtension.getProducerTemplate();
    }

    @Test
    void shouldSupportConsecutiveTemporaryReplyRequests() {
        assertThat(template.requestBody("jms:queue:" + REQUEST_QUEUE, "first", String.class))
                .isEqualTo("echo:first");
        assertThat(template.requestBody("jms:queue:" + REQUEST_QUEUE, "second", String.class))
                .isEqualTo("echo:second");
    }
}
