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
package org.apache.camel.tracing.propagation;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Deprecated(since = "4.19.0")
public class CamelHeadersInjectAdapterTest {

    @Test
    public void camelHeadersInAnyCaseAreNotInjected() {
        Map<String, Object> map = new HashMap<>();
        CamelHeadersInjectAdapter adapter = new CamelHeadersInjectAdapter(map);
        adapter.put("CamelFoo", "value1");
        adapter.put("camelFoo", "value2");
        adapter.put("CAMELFOO", "value3");
        adapter.put("CaMeLfoo", "value4");
        adapter.put("traceparent", "value5");
        assertEquals(1, map.size());
        assertEquals("value5", map.get("traceparent"));
    }
}
