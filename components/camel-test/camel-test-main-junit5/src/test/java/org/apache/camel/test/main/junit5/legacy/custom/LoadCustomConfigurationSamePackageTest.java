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
package org.apache.camel.test.main.junit5.legacy.custom;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.main.MainConfigurationProperties;
import org.apache.camel.test.main.junit5.CamelMainTestSupport;
import org.apache.camel.test.main.junit5.common.MyConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The test class ensuring that a custom property placeholder can be specified with the name of the file located in the
 * same package.
 */
class LoadCustomConfigurationSamePackageTest extends CamelMainTestSupport {

    @Override
    protected String getPropertyPlaceholderFileName() {
        return "custom-same-package-application.properties";
    }

    @Override
    protected void configure(MainConfigurationProperties configuration) {
        // Add the configuration class
        configuration.addConfiguration(MyConfiguration.class);
        // Add all the XML routes
        configuration.withRoutesIncludePattern("routes/*.xml");
    }

    @Test
    void shouldFindCustomConfiguration() throws Exception {
        MockEndpoint mock = context.getEndpoint("mock:out", MockEndpoint.class);
        mock.expectedBodiesReceived("Hello John!");
        String result = template.requestBody("direct:in", null, String.class);
        mock.assertIsSatisfied();
        assertEquals("Hello John!", result);
    }
}
