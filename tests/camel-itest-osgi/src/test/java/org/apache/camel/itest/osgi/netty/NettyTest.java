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
package org.apache.camel.itest.osgi.netty;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.itest.osgi.OSGiIntegrationTestSupport;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;

import static org.ops4j.pax.exam.OptionUtils.combine;

@RunWith(PaxExam.class)
public class NettyTest extends OSGiIntegrationTestSupport {
    
    @Test
    public void testNetty() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("World");

        String reply = template.requestBody("netty:tcp://localhost:8876?textline=true", "World", String.class);
        assertEquals("Bye World", reply);

        assertMockEndpointsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("netty:tcp://localhost:8876?textline=true")
                    .to("mock:result")
                    .transform(body().prepend("Bye "));
            }
        };
    }
    
    @Configuration
    public static Option[] configure() {
        Option[] options = combine(
            getDefaultCamelKarafOptions(),
            // using the features to install the other camel components             
            loadCamelFeatures("camel-netty"));
        
        return options;
    }
    
}