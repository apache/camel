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
package org.apache.camel.test.junit5.patterns;

import java.util.Properties;

import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.reifier.RouteReifier;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class UseOverridePropertiesWithPropertiesComponentTest extends CamelTestSupport {

    @Override
    public boolean isUseAdviceWith() {
        return true;
    }

    @BeforeEach
    public void doSomethingBefore() throws Exception {
        AdviceWithRouteBuilder mocker = new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                replaceFromWith("direct:sftp");

                interceptSendToEndpoint("file:*").skipSendToOriginalEndpoint().to("mock:file");
            }
        };
        RouteReifier.adviceWith(context.getRouteDefinition("myRoute"), context, mocker);
    }

    @Override
    protected Properties useOverridePropertiesWithPropertiesComponent() {
        Properties pc = new Properties();
        pc.put("ftp.username", "scott");
        pc.put("ftp.password", "tiger");
        return pc;
    }

    @Test
    public void testOverride() throws Exception {
        context.start();

        getMockEndpoint("mock:file").expectedMessageCount(1);

        template.sendBody("direct:sftp", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("ftp:somepath?username={{ftp.username}}&password={{ftp.password}}").routeId("myRoute").to("file:target/out");
            }
        };
    }
}
