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

import com.github.dockerjava.api.model.Version;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class DockerCustomCmdExecFactoryTestIT extends DockerITTestSupport  {

    @Test
    public void testNettyCmdExecFactoryConfig() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived(FakeDockerCmdExecFactory.FAKE_VERSION);

        template.sendBody("direct:in", "");

        assertMockEndpointsSatisfied(60, TimeUnit.SECONDS);
        mock.getExchanges();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        FakeDockerCmdExecFactory.class.getDeclaredConstructors();

        return new RouteBuilder() {
            public void configure() {
                from("direct:in")
                    .to("docker://version?cmdExecFactory=" + FakeDockerCmdExecFactory.class.getName())
                    .log("${body}")
                    .process(exchange -> {
                        Version version = exchange.getIn().getBody(Version.class);
                        exchange.getMessage().setBody(version.getVersion());
                    })
                    .to("mock:result");
            }
        };
    }

}
