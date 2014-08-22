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
package org.apache.camel.itest.osgi.core;

import org.apache.camel.itest.osgi.OSGiIntegrationTestSupport;
import org.apache.camel.spi.FactoryFinder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;

@RunWith(PaxExam.class)
public class OsgiFactoryFinderTest extends OSGiIntegrationTestSupport {
    
    @Test
    public void testFileProducer() throws Exception {        
        FactoryFinder finder = context.getFactoryFinder("META-INF/services/org/apache/camel/component/");
        Class<?> factory = finder.findClass("file", "strategy.factory.");
        assertNotNull("We should find the factory here.", factory);
    }
  
    @Before
    public void setUp() throws Exception {
        setUseRouteBuilder(false);
        super.setUp();        
    }
    
}
