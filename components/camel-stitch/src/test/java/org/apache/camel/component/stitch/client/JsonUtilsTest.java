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
package org.apache.camel.component.stitch.client;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonUtilsTest {

    @Test
    void invalidJsonFailsWithRuntimeException() {
        RuntimeException e = assertThrows(RuntimeException.class, () -> JsonUtils.convertJsonToMap("not json"));

        assertEquals("Error occurred writing JSON to Map.", e.getMessage());
    }

    @Test
    void mapKeepsItsOrder() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("z", 1);
        map.put("a", 2);

        assertEquals("{\"z\":1,\"a\":2}", JsonUtils.convertMapToJson(map));
    }
}
