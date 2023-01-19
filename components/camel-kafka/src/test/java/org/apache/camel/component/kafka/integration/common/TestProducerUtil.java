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

package org.apache.camel.component.kafka.integration.common;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.ProducerTemplate;

public final class TestProducerUtil {

    private TestProducerUtil() {

    }

    public static void sendMessagesInRoute(
            String uri, int messages, ProducerTemplate template, Object bodyOther, String... headersWithValue) {
        Map<String, Object> headerMap = new HashMap<>();
        if (headersWithValue != null) {
            for (int i = 0; i < headersWithValue.length; i = i + 2) {
                headerMap.put(headersWithValue[i], headersWithValue[i + 1]);
            }
        }
        sendMessagesInRoute(uri, messages, template, bodyOther, headerMap);
    }

    public static void sendMessagesInRoute(
            String uri, int messages, ProducerTemplate template, Object bodyOther, Map<String, Object> headerMap) {
        for (int k = 0; k < messages; k++) {
            template.sendBodyAndHeaders(uri, bodyOther, headerMap);
        }
    }
}
