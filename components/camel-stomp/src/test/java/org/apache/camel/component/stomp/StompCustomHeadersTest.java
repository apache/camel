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
package org.apache.camel.component.stomp;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.Test;

public class StompCustomHeadersTest extends StompBaseTest {

    @Test
    public void testConsume() throws Exception {
        if (!canTest()) {
            return;
        }

        Map<String, Object> map = new HashMap<String, Object>();
        map.put("brokerURL", "tcp://localhost:61613");
        map.put("version", "1.2");

        Properties props = new Properties();
        props.setProperty("ack", "auto");

        map.put("customHeaders", props);

        // ensure component is initialized
        context.getComponent("stomp").init();

        StompEndpoint endpoint = (StompEndpoint) context.getEndpoint("stomp:test", map);
        assertNotNull(endpoint.getConfiguration().getCustomHeaders().getProperty("ack"));
    }
}
