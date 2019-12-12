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
package org.apache.camel.component.robotframework;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

/**
 * Represents the component that manages {@link RobotFrameworkEndpoint}.
 */
@Component("robotframework")
public class RobotFrameworkComponent extends DefaultComponent {

    @Metadata(label = "advanced")
    private RobotFrameworkCamelConfiguration configuration;

    public RobotFrameworkComponent(CamelContext context) {
        super(context);
        this.configuration = new RobotFrameworkCamelConfiguration();
    }

    public RobotFrameworkComponent() {
        this(null);
    }

    /**
     * The configuration
     */
    public void setConfiguration(RobotFrameworkCamelConfiguration configuration) {
        this.configuration = configuration;
    }
    
    public RobotFrameworkCamelConfiguration getConfiguration() {
        return configuration;
    }

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        final RobotFrameworkCamelConfiguration configuration = this.configuration.copy();

        Endpoint endpoint = new RobotFrameworkEndpoint(uri, this, remaining, configuration);
        setProperties(endpoint, parameters);
        return endpoint;
    }
}
