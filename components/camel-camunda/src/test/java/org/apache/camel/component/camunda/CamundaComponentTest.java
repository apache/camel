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
package org.apache.camel.component.camunda;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CamundaComponentTest {

    @Test
    void verifyDefaults() {
        CamundaComponent component = new CamundaComponent();

        assertEquals("http://localhost:26500", component.getGrpcAddress());
        assertEquals("http://localhost:8080", component.getRestAddress());
        assertEquals("bru-2", component.getRegion());
        assertNull(component.getClusterId());
        assertNull(component.getClientId());
        assertNull(component.getClientSecret());
        assertNull(component.getOAuthAPI());
    }
}
