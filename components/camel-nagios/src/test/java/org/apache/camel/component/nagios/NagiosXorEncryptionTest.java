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
package org.apache.camel.component.nagios;

import com.googlecode.jsendnsca.core.Level;
import com.googlecode.jsendnsca.core.MessagePayload;
import com.googlecode.jsendnsca.core.NagiosPassiveCheckSender;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @version 
 */
public class NagiosXorEncryptionTest extends CamelTestSupport {
    protected boolean canRun;

    @Mock
    private NagiosPassiveCheckSender nagiosPassiveCheckSender = Mockito.mock(NagiosPassiveCheckSender.class);

    @Before
    @Override
    public void setUp() throws Exception {
        canRun = true;
        super.setUp();
    }
    
    @Test
    public void testSendToNagios() throws Exception {
        if (!canRun) {
            return;
        }

        MessagePayload expectedPayload = new MessagePayload("localhost", Level.OK.ordinal(), context.getName(),  "Hello Nagios");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.allMessages().body().isInstanceOf(String.class);

        template.sendBody("direct:start", "Hello Nagios");

        assertMockEndpointsSatisfied();

        verify(nagiosPassiveCheckSender, times(1)).send(expectedPayload);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                String uri = "nagios:127.0.0.1:25664?password=secret&encryptionMethod=Xor";

                NagiosComponent nagiosComponent = new NagiosComponent();
                nagiosComponent.setCamelContext(context);
                NagiosEndpoint nagiousEndpoint = (NagiosEndpoint) nagiosComponent.createEndpoint(uri);
                nagiousEndpoint.setSender(nagiosPassiveCheckSender);
                Producer nagiosProducer = nagiousEndpoint.createProducer();

                from("direct:start")
                        .to(nagiousEndpoint)
                        .to("mock:result");
            }
        };
    }

}