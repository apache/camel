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
package org.apache.camel.component.netty;

import java.io.File;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

/**
 * To run this test manually through Maven first remove the {@link Ignore}
 * annotation below, then make sure you've got a network interface with the name
 * <code>en0</code> as given by the route below. If this is not the case run
 * your OS specific command to find out which network interfaces you've got
 * supporting IPv4 (e.g. on OS-X that would be the {@code ifconfig -a} command.
 * Next you need to enable UDP multicast on your OS for the given multicast
 * address of this test ({@code 224.1.2.3}). For this purpose e.g. on OS-X
 * follow the steps being described <a href=
 * "http://blogs.agilefaqs.com/2009/11/08/enabling-multicast-on-your-macos-unix/"
 * >here</a>. Now running the test manually should succeed (<b>but only</b> when
 * using Java7+):
 * 
 * <pre>
 *   mvn test -Djava.net.preferIPv4Stack=true -Dtest=NettyUDPMulticastAsyncTest
 * </pre>
 * 
 * Please note that using the JUnit {@link BeforeClass} annotation together with
 * the corresponding {@link System#setProperty(String, String) Java API} to set
 * the {@code java.net.preferIPv4Stack} system property would not work here as
 * at that moment it would be too <b>late</b> to set this system property. On
 * the other hand setting such a system property through the surefire-plugin
 * would cause side effect by the other tests of this component.
 */
@Ignore("See the Javadoc")
public class NettyUDPMulticastAsyncTest extends BaseNettyTest {

    private void sendFile(String uri) throws Exception {
        template.send(uri, new Processor() {
            public void process(Exchange exchange) throws Exception {
                byte[] buffer = exchange.getContext().getTypeConverter().mandatoryConvertTo(byte[].class, new File("src/test/resources/test.txt"));
                exchange.setProperty(Exchange.CHARSET_NAME, "ASCII");
                exchange.getIn().setBody(buffer);
            }
        });
    }

    @Test
    public void testUDPInOnlyMulticastWithNettyConsumer() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().startsWith("Song Of A Dream".getBytes());

        // any IP in the range of 224.0.0.0 through 239.255.255.255 does the job
        sendFile("netty:udp://224.1.2.3:{{port}}?sync=false");

        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("netty:udp://224.1.2.3:{{port}}?sync=false&networkInterface=en0")
                    .to("mock:result")
                    .to("log:Message"); 
            }
        };
    }

}
