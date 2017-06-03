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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultMessage;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DockerConfigurationTest {

    private DockerConfiguration configuration;
    private CamelContext camelContext;

    @Before
    public void setupTest() {
        configuration = new DockerConfiguration();
        camelContext = new DefaultCamelContext();
    }

    @Test
    public void testPropertyFromHeader() {
        String host = "camelhost";

        Message message = new DefaultMessage(camelContext);
        message.setHeader(DockerConstants.DOCKER_HOST, host);

        String configurationProp = DockerHelper.getProperty(DockerConstants.DOCKER_HOST, configuration, message, String.class);
        assertEquals(host, configurationProp);
    }

    @Test
    public void testPropertyfromEndpointProperties() {
        String host = "camelhost";

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put(DockerHelper.transformFromHeaderName(DockerConstants.DOCKER_HOST), host);
        configuration.setParameters(parameters);

        Message message = new DefaultMessage(camelContext);
        String configurationProp = DockerHelper.getProperty(DockerConstants.DOCKER_HOST, configuration, message, String.class);
        assertEquals(host, configurationProp);
    }

}
