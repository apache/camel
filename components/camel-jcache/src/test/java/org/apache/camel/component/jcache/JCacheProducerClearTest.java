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
package org.apache.camel.component.jcache;

import java.util.HashMap;
import java.util.Map;
import javax.cache.Cache;

import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class JCacheProducerClearTest extends JCacheComponentTestSupport {

    @Test
    public void testClear() throws Exception {
        final Map<String, Object> headers = new HashMap<>();
        final Cache<Object, Object> cache = getCacheFromEndpoint("jcache://test-cache");

        cache.putAll(generateRandomMap(4));

        headers.clear();
        headers.put(JCacheConstants.ACTION, "CLEAR");
        sendBody("direct:clear", null, headers);

        assertFalse(cache.iterator().hasNext());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:clear")
                    .to("jcache://test-cache");
            }
        };
    }
}
