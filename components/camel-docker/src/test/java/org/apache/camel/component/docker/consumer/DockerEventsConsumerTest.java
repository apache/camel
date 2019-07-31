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
package org.apache.camel.component.docker.consumer;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.EventsCmd;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.docker.DockerComponent;
import org.apache.camel.component.docker.DockerConfiguration;
import org.apache.camel.component.docker.util.DockerTestUtils;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.ArgumentMatchers.anyString;

/**
 * Consumer test for events on Docker Platform
 */
@RunWith(MockitoJUnitRunner.class)
public class DockerEventsConsumerTest extends CamelTestSupport {
    private String host = "localhost";
    private Integer port = 2375;

    private DockerConfiguration dockerConfiguration;

    @Mock
    private EventsCmd eventsCmd;

    @Mock
    private DockerClient dockerClient;

    public void setupMocks() {
        Mockito.when(dockerClient.eventsCmd()).thenReturn(eventsCmd);
        Mockito.when(eventsCmd.withSince(anyString())).thenReturn(eventsCmd);
    }

    @Test
    public void testEvent() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("docker://events?host=" + host + "&port=" + port).log("${body}").to("mock:result");
            }
        };
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        dockerConfiguration = new DockerConfiguration();
        dockerConfiguration.setParameters(DockerTestUtils.getDefaultParameters(host, port, dockerConfiguration));

        DockerComponent dockerComponent = new DockerComponent(dockerConfiguration);
        dockerComponent.setClient(DockerTestUtils.getClientProfile(host, port, dockerConfiguration), dockerClient);
        camelContext.addComponent("docker", dockerComponent);

        setupMocks();

        return camelContext;
    }

}
