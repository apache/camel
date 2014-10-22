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
package org.apache.camel.component.docker.headers;

import com.github.dockerjava.api.DockerClient;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.docker.DockerClientProfile;
import org.apache.camel.component.docker.DockerComponent;
import org.apache.camel.component.docker.DockerConfiguration;
import org.apache.camel.component.docker.DockerConstants;
import org.apache.camel.component.docker.DockerOperation;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
public abstract class BaseDockerHeaderTest<T> extends CamelTestSupport {
    
    @Mock
    protected DockerClient dockerClient;
        
    protected DockerConfiguration dockerConfiguration;    
    
    @Mock
    T mockObject;
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            
            @Override
            public void configure() throws Exception {
                from("direct:in").to("docker://"+getOperation().toString());
                
            }
        };
        
    }   
    
    @Before
    public void setupTest() {
        setupMocks();
    }
    
    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        dockerConfiguration = new DockerConfiguration();
        dockerConfiguration.setParameters(getDefaultParameters());
        
        
        dockerConfiguration.setClient(getClientProfile(), dockerClient);
        
        DockerComponent dockerComponent = new DockerComponent(dockerConfiguration);
        camelContext.addComponent("docker", dockerComponent);
        

        
        return camelContext;
    }
    
    
    protected String getHost() {
        return "localhost";
    }
    
    protected Integer getPort() {
        return 5000;
    }
    
    protected String getEmail() {
        return "docker@camel.apache.org";
    }
    
    public T getMockObject() {
        return mockObject;
    }
    
    protected Map<String,Object> getDefaultParameters() {
        Map<String,Object> parameters = new HashMap<String,Object>();
        parameters.put(DockerConstants.DOCKER_HOST, getHost());
        parameters.put(DockerConstants.DOCKER_PORT, getPort());
        parameters.put(DockerConstants.DOCKER_EMAIL, getEmail());

        return parameters;
    }
    
    protected DockerClientProfile getClientProfile() {
        DockerClientProfile clientProfile = new DockerClientProfile();
        clientProfile.setHost(getHost());
        clientProfile.setPort(getPort());
        clientProfile.setEmail(getEmail());
        
        return clientProfile;

    }
    
    
    protected abstract void setupMocks();
    
    protected abstract DockerOperation getOperation();
         
    
}
