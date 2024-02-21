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
package org.apache.camel.component.jetty;

import java.util.Map;

import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.context.support.AbstractApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 *
 */
public class JettyComponentSpringConfiguredTest extends CamelSpringTestSupport {

    @RegisterExtension
    protected AvailablePortFinder.Port port = AvailablePortFinder.find();

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return newAppContext("JettyComponentSpringConfiguredTest.xml");
    }

    protected Map<String, String> getTranslationProperties() {
        Map<String, String> map = super.getTranslationProperties();
        map.put("port", port.toString());
        return map;
    }

    @Test
    @Disabled("run manual test")
    public void testJetty2() {
        assertNotNull(context.hasComponent("jetty2"), "Should have jetty2 component");

        String reply = template.requestBody("http://localhost:" + port + "/myapp", "Camel", String.class);
        assertEquals("Hello Camel", reply);
    }
}
