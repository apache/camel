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
package org.apache.camel.component.kestrel;

import java.util.Arrays;

import org.apache.camel.CamelContext;
import org.apache.camel.test.junit4.CamelTestSupport;

import org.junit.Test;

public class KestrelEndpointTest extends CamelTestSupport {

    private static final class TestCase {
        String uri;
        String[] addresses;
        String queue;
        Integer waitTimeMs;
        Integer concurrentConsumers;
        TestCase(String uri, String[] addresses, String queue, Integer waitTimeMs, Integer concurrentConsumers) {
            this.uri = uri;
            this.addresses = addresses;
            this.queue = queue;
            this.waitTimeMs = waitTimeMs;
            this.concurrentConsumers = concurrentConsumers;
        }
    }
    
    private static final TestCase[] TEST_CASES = new TestCase[] {
        new TestCase("kestrel:///queuename",
                     null,
                     "queuename",
                     null,
                     null),
        new TestCase("kestrel://queuename?concurrentConsumers=44",
                     null,
                     "queuename",
                     null,
                     44),
        new TestCase("kestrel://queuename?waitTimeMs=4567",
                     null,
                     "queuename",
                     4567,
                     null),
        new TestCase("kestrel://localhost/queuename",
                     new String[] {"localhost"},
                     "queuename",
                     null,
                     null),
        new TestCase("kestrel://127.0.0.1:22133,localhost:22134/queuename?waitTimeMs=4567&concurrentConsumers=99",
                     new String[] {"127.0.0.1:22133", "localhost:22134"},
                     "queuename",
                     4567,
                     99),
        new TestCase("kestrel://127.0.0.1:22133/queuename?concurrentConsumers=10&waitTimeMs=4567",
                     new String[] {"127.0.0.1:22133"},
                     "queuename",
                     4567,
                     10),
        new TestCase("kestrel://localhost/queuename?concurrentConsumers=20",
                     new String[] {"localhost"},
                     "queuename",
                     null,
                     20),
        new TestCase("kestrel://localhost,otherhost/queuename?waitTimeMs=4567",
                     new String[] {"localhost", "otherhost"},
                     "queuename",
                     4567,
                     null),
        new TestCase("kestrel://localhost:22133,otherhost/queuename?waitTimeMs=4567&concurrentConsumers=5",
                     new String[] {"localhost:22133", "otherhost"},
                     "queuename",
                     4567,
                     5),
        new TestCase("kestrel://localhost,otherhost:22133/queuename?waitTimeMs=4567",
                     new String[] {"localhost", "otherhost:22133"},
                     "queuename",
                     4567,
                     null),
    };

    private KestrelConfiguration baseConfiguration;
    private KestrelComponent kestrelComponent;
    
    @Test
    public void testEndpoints() throws Exception {
        for (TestCase testCase : TEST_CASES) {
            KestrelEndpoint endpoint = (KestrelEndpoint)
                resolveMandatoryEndpoint(testCase.uri);
            assertEquals("getQueue(" + testCase.uri + ")", testCase.queue, endpoint.getQueue());
            
            KestrelConfiguration configuration = endpoint.getConfiguration();
            
            if (testCase.addresses != null) {
                assertEquals("getAddresses(" + testCase.uri + ")", Arrays.asList(testCase.addresses), Arrays.asList(configuration.getAddresses()));
            } else {
                assertEquals("getAddresses(" + testCase.uri + ")", Arrays.asList(baseConfiguration.getAddresses()), Arrays.asList(configuration.getAddresses()));
            }
            
            if (testCase.waitTimeMs != null) {
                assertEquals("getWaitTimeMs(" + testCase.uri + ")", (Object)testCase.waitTimeMs, configuration.getWaitTimeMs());
            } else { 
                assertEquals("getWaitTimeMs(" + testCase.uri + ")", baseConfiguration.getWaitTimeMs(), configuration.getWaitTimeMs());
            }
            
            if (testCase.concurrentConsumers != null) {
                assertEquals("getConcurrentConsumers(" + testCase.uri + ")", (Object)testCase.concurrentConsumers, configuration.getConcurrentConsumers());
            } else {
                assertEquals("getConcurrentConsumers(" + testCase.uri + ")", baseConfiguration.getConcurrentConsumers(), configuration.getConcurrentConsumers());
            }
        }
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        baseConfiguration = new KestrelConfiguration();
        baseConfiguration.setAddresses(new String[] {"base:12345"});
        baseConfiguration.setWaitTimeMs(9999);
        baseConfiguration.setConcurrentConsumers(11);
        
        kestrelComponent = new KestrelComponent();
        kestrelComponent.setConfiguration(baseConfiguration);

        CamelContext camelContext = super.createCamelContext();
        camelContext.addComponent("kestrel", kestrelComponent);

        return camelContext;
    }
}
