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
package org.apache.camel.component.ganglia;

import java.net.URI;
import java.net.URISyntaxException;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * JUnit test class for <code>org.apache.camel.component.ganglia.GangliaConfiguration</code>
 * 
 * @version 
 */
public class GangliaConfigurationTest {
    
    private GangliaConfiguration configuration;

    @Before
    public void setUp() {
        configuration = new GangliaConfiguration();
    }

    @Test
    public void getterShouldReturnTheDefaultValues() {
        assertEquals("239.2.11.71", configuration.getHost());
        assertEquals(8649, configuration.getPort());
        assertEquals(true, configuration.getWireFormat31x());
    }
    
    @Test
    public void getterShouldReturnTheSetValues() {
        setNonDefaultValues(configuration);
        
        assertEquals("10.10.1.1", configuration.getHost());
        assertEquals(18649, configuration.getPort());
        assertEquals(false, configuration.getWireFormat31x());
    }

    @Test
    public void getterShouldReturnTheConfigureValuesFromURI() throws URISyntaxException {
        configuration.configure(new URI("ganglia://192.168.1.1:28649?mode=UNICAST"));
        
        assertEquals("192.168.1.1", configuration.getHost());
        assertEquals(28649, configuration.getPort());
    }
    
    @Test
    public void cloneShouldReturnAnEqualInstance() {
        setNonDefaultValues(configuration);
        GangliaConfiguration config = configuration.copy();
        
        assertEquals(config.getHost(), configuration.getHost());
        assertEquals(config.getPort(), configuration.getPort());
    }
    
    private void setNonDefaultValues(GangliaConfiguration config) {
        config.setHost("10.10.1.1");
        config.setPort(18649);
        config.setWireFormat31x(false);
    }
}
