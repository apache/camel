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
package org.apache.camel.component.docker.it;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.component.docker.DockerComponent;
import org.apache.camel.component.docker.DockerConfiguration;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.IntrospectionSupport;

public class DockerITTestSupport extends CamelTestSupport {

    private static final String TEST_OPTIONS_PROPERTIES = "/test-options.properties";

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        // Read Docker component configuration properties
        Properties properties = new Properties();
        properties.load(getClass().getResourceAsStream(TEST_OPTIONS_PROPERTIES));

        Map<String, Object> options = new HashMap<>();
        properties.entrySet().forEach((e) -> options.put(e.getKey().toString(), e.getValue()));

        DockerConfiguration configuration = new DockerConfiguration();
        IntrospectionSupport.setProperties(configuration, options);

        DockerComponent component = new DockerComponent();
        component.setConfiguration(configuration);
        context.addComponent("docker", component);

        return context;
    }
}
