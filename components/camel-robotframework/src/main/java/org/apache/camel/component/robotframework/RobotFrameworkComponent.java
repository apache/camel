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

    public boolean isAllowTemplateFromHeader() {
        return configuration.isAllowTemplateFromHeader();
    }

    /**
     * Whether to allow to use resource template from header or not (default false).
     *
     * Enabling this allows to specify dynamic templates via message header. However this can be seen as a potential
     * security vulnerability if the header is coming from a malicious user, so use this with care.
     */
    public void setAllowTemplateFromHeader(boolean allowTemplateFromHeader) {
        configuration.setAllowTemplateFromHeader(allowTemplateFromHeader);
    }

    public boolean isAllowContextMapAll() {
        return configuration.isAllowContextMapAll();
    }

    /**
     * Sets whether the context map should allow access to all details. By default only the message body and headers can
     * be accessed. This option can be enabled for full access to the current Exchange and CamelContext. Doing so impose
     * a potential security risk as this opens access to the full power of CamelContext API.
     */
    public void setAllowContextMapAll(boolean allowContextMapAll) {
        configuration.setAllowContextMapAll(allowContextMapAll);
    }

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        final RobotFrameworkCamelConfiguration configuration = this.configuration.copy();

        RobotFrameworkEndpoint endpoint = new RobotFrameworkEndpoint(uri, this, remaining, configuration);
        setProperties(endpoint, parameters);
        return endpoint;
    }

}
