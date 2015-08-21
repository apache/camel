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
package org.apache.camel.component.docker;

import java.util.concurrent.TimeUnit;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.StatsCallback;
import com.github.dockerjava.api.command.StatsCmd;
import com.github.dockerjava.api.model.Statistics;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.docker.util.DockerTestUtils;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.modules.junit4.PowerMockRunner;


/**
 * Consumer test for statistics on Docker Platform
 */
@RunWith(PowerMockRunner.class)
public class DockerStatsConsumerTest extends CamelTestSupport {

    private String host = "localhost";
    private Integer port = 2375;
    private String containerId = "470b9b823e6c";
    private StatsCallback callback;
    
    private DockerConfiguration dockerConfiguration;
    
    @Mock
    private StatsCmd statsCmd;
    
    @Mock
    private DockerClient dockerClient;

    public void setupMocks() {
        Mockito.when(dockerClient.statsCmd(Mockito.any(StatsCallback.class))).thenAnswer(new Answer<StatsCmd>() {
            public StatsCmd answer(InvocationOnMock invocation) {
                Object[] args = invocation.getArguments();
                callback = (StatsCallback)args[0];
                return statsCmd;
            }
        });
        
      
    }

    @Test
    public void testStats() throws Exception {

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);

        callback.onStats(new Statistics());
                
        assertMockEndpointsSatisfied(10, TimeUnit.SECONDS);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("docker://stats?host=" + host + "&port=" + port + "&containerId=" + containerId)
                        .log("${body}")
                        .to("mock:result");
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
