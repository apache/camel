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
package org.apache.camel.processor.interceptor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.impl.DefaultRouteContext;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.spi.RouteContext;

/**
 * Test cases for {@link StreamCaching}
 */
public class StreamCachingTest extends ContextTestSupport {

    /**
     * Tests enabling stream caching on a {@link RouteContext}
     */
    public void testEnableOnRouteContext() throws Exception {
        RouteContext rc = new DefaultRouteContext(super.context);
        StreamCaching.enable(rc);
        assertStrategyEnabled("Enabling StreamCaching should add it to the intercept strategies", rc);
        StreamCaching.enable(rc);
        assertStrategyEnabled("Enabling it again should not add a second instance", rc);
    }

    /*
     * Assert that the strategy is enabled exactly one time
     */
    private void assertStrategyEnabled(String message, RouteContext rc) {
        int count = 0;
        for (InterceptStrategy strategy : rc.getInterceptStrategies()) {
            if (strategy instanceof StreamCaching) {
                count++;
            }
        }
        assertEquals(message, 1, count);
    }

}
