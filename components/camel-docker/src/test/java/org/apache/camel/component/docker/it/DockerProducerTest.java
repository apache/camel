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
package org.apache.camel.component.docker.it;

import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Integration test listing images on Docker Platform. For this test to run you need to inform the docker host name via
 * -Ddocker.hostname=my.host.com and the port via -Ddocker.port=2375
 */
@EnabledIfSystemProperty(named = "docker.hostname", matches = ".*", disabledReason = "Requires a running docker environment")
public class DockerProducerTest extends DockerITTestSupport {

    @Test
    void testDocker() throws Exception {
        template.sendBody("direct:in", "");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);

        MockEndpoint.assertIsSatisfied(context, 60, TimeUnit.SECONDS);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:in")
                        .to("docker://imagelist?maxTotalConnections=10&host={{docker.hostname}}&port={{docker.port}}")
                        .log("${body}")
                        .to("mock:result");
            }
        };
    }
}
