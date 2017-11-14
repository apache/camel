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

import com.googlecode.jsendnsca.NonBlockingNagiosPassiveCheckSender;

import org.apache.camel.builder.RouteBuilder;
import org.junit.BeforeClass;
import org.mockito.Mockito;

/**
 * @version 
 */
public class NagiosAsyncSendTest extends NagiosTest {

    @BeforeClass
    public static void setSender() {
        nagiosPassiveCheckSender =  Mockito.mock(NonBlockingNagiosPassiveCheckSender.class);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                String uri = "nagios:127.0.0.1:25664?password=secret&sendSync=false";

                NagiosComponent nagiosComponent = new NagiosComponent();
                nagiosComponent.setCamelContext(context);
                NagiosEndpoint nagiosEndpoint = (NagiosEndpoint) nagiosComponent.createEndpoint(uri);
                nagiosEndpoint.setSender(nagiosPassiveCheckSender);
                nagiosEndpoint.createProducer();

                from("direct:start")
                        .to(nagiosEndpoint)
                        .to("mock:result");
            }
        };
    }

}