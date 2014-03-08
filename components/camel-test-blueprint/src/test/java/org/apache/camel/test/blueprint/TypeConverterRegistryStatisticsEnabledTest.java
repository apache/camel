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
package org.apache.camel.test.blueprint;

import org.apache.camel.spi.TypeConverterRegistry;
import org.junit.Test;

public class TypeConverterRegistryStatisticsEnabledTest extends CamelBlueprintTestSupport {

    @Override
    protected String getBlueprintDescriptor() {
        return "org/apache/camel/test/blueprint/typeConverterRegistryStatisticsEnabledTest.xml";
    }

    @Test
    public void testTypeConverterRegistry() throws Exception {
        getMockEndpoint("mock:a").expectedMessageCount(2);

        template.sendBody("direct:start", "3");
        template.sendBody("direct:start", "7");

        assertMockEndpointsSatisfied();

        TypeConverterRegistry reg = context.getTypeConverterRegistry();
        assertTrue("Should be enabled", reg.getStatistics().isStatisticsEnabled());

        Long failed = reg.getStatistics().getFailedCounter();
        assertEquals(0, failed.intValue());
        Long miss = reg.getStatistics().getMissCounter();
        assertEquals(0, miss.intValue());

        try {
            template.sendBody("direct:start", "foo");
            fail("Should have thrown exception");
        } catch (Exception e) {
            // expected
        }

        // should now have a failed
        failed = reg.getStatistics().getFailedCounter();
        assertEquals(1, failed.intValue());
        miss = reg.getStatistics().getMissCounter();
        assertEquals(0, miss.intValue());

        // reset
        reg.getStatistics().reset();

        failed = reg.getStatistics().getFailedCounter();
        assertEquals(0, failed.intValue());
        miss = reg.getStatistics().getMissCounter();
        assertEquals(0, miss.intValue());
    }

}
