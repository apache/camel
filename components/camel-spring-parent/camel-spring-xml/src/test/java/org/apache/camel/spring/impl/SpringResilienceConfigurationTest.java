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
package org.apache.camel.spring.impl;

import org.apache.camel.model.FaultToleranceConfigurationDefinition;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.Resilience4jConfigurationDefinition;
import org.apache.camel.spring.SpringTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SpringResilienceConfigurationTest extends SpringTestSupport {

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/impl/SpringResilienceConfigurationTest.xml");
    }

    @Test
    public void testResilienceConfigurations() {
        ModelCamelContext mcc = (ModelCamelContext) context;

        Resilience4jConfigurationDefinition r4j = mcc.getResilience4jConfiguration(null);
        assertNotNull(r4j);
        assertEquals("40", r4j.getFailureRateThreshold());
        r4j = mcc.getResilience4jConfiguration("myR4j");
        assertNotNull(r4j);
        assertEquals("25", r4j.getFailureRateThreshold());

        FaultToleranceConfigurationDefinition ft = mcc.getFaultToleranceConfiguration(null);
        assertNotNull(ft);
        assertEquals("3000", ft.getDelay());
        ft = mcc.getFaultToleranceConfiguration("myFt");
        assertNotNull(ft);
        assertEquals("2000", ft.getDelay());
    }
}
