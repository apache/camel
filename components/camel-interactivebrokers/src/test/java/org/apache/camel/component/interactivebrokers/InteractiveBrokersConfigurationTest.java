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
package org.apache.camel.component.interactivebrokers;

import java.net.URI;
import java.net.URISyntaxException;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * JUnit test class for <code>org.apache.camel.component.interactiveBrokers.InteractiveBrokersConfiguration</code>
 * 
 * @version 
 */
public class InteractiveBrokersConfigurationTest {
    
    private InteractiveBrokersConfiguration configuration;

    @Before
    public void setUp() {
        configuration = new InteractiveBrokersConfiguration();
    }

    @Test
    public void getterShouldReturnTheDefaultValues() {
        assertEquals("localhost", configuration.getHost());
        assertEquals(7496, configuration.getPort());
        assertEquals(0, configuration.getClientId());
    }
    
    @Test
    public void getterShouldReturnTheSetValues() {
        setNonDefaultValues(configuration);
        
        assertEquals("192.168.1.100", configuration.getHost());
        assertEquals(20000, configuration.getPort());
        assertEquals(1, configuration.getClientId());
    }

    @Test
    public void getterShouldReturnTheConfigureValuesFromURI() throws URISyntaxException {
        configuration.configure(new URI("interactiveBrokers://192.168.1.200:5555?clientId=3"));
        
        assertEquals("192.168.1.200", configuration.getHost());
        assertEquals(5555, configuration.getPort());
        assertEquals(3, configuration.getClientId());
    }
    
    @Test
    public void cloneShouldReturnAnEqualInstance() {
        setNonDefaultValues(configuration);
        InteractiveBrokersConfiguration config = configuration.copy();
        
        assertEquals(config.getHost(), configuration.getHost());
        assertEquals(config.getPort(), configuration.getPort());
        assertEquals(config.getClientId(), configuration.getClientId());
    }
    
    private void setNonDefaultValues(InteractiveBrokersConfiguration config) {
        config.setHost("192.168.1.100");
        config.setPort(20000);
        config.setClientId(1);
    }
}
