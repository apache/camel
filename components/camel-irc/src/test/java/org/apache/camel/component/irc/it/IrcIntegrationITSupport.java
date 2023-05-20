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
package org.apache.camel.component.irc.it;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.camel.EndpointInject;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IrcIntegrationITSupport extends CamelTestSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(IrcIntegrationITSupport.class);

    @EndpointInject("mock:result")
    protected MockEndpoint resultEndpoint;

    protected Properties properties;

    @BeforeEach
    public void doBefore() throws IOException {
        properties = loadProperties();
        resetMock(resultEndpoint);
    }

    protected void resetMock(MockEndpoint mock) {
        mock.reset();
        mock.setResultWaitTime(TimeUnit.MINUTES.toMillis(1));
    }

    private Properties loadProperties() throws IOException {
        Properties p = new Properties();
        p.load(this.getClass().getResourceAsStream("/it-tests.properties"));
        return p;
    }

    @Override
    protected Properties useOverridePropertiesWithPropertiesComponent() {
        try {
            return loadProperties();
        } catch (IOException e) {
            LOGGER.error("Can't load configuration properties");
            return null;
        }
    }

    protected String sendUri() {
        return "ircs://{{camelTo}}@{{server}}?channels={{channel1}}";
    }

    protected String fromUri() {
        return "ircs://{{camelFrom}}@{{server}}?&channels={{channel1}}";
    }
}
