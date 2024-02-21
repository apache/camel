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
package org.apache.camel.catalog;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.spi.EndpointUriFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class RuntimeCamelCatalogEndpointUriAssemblerTest extends ContextTestSupport {

    @Test
    public void testLookupAssemble() throws Exception {
        EndpointUriFactory assembler = context.getCamelContextExtension().getEndpointUriFactory("timer");

        Map<String, Object> params = new HashMap<>();
        params.put("timerName", "foo");
        params.put("period", "123");
        params.put("repeatCount", "5");

        String uri = assembler.buildUri("timer", params);
        Assertions.assertEquals("timer:foo?period=123&repeatCount=5", uri);
    }

}
