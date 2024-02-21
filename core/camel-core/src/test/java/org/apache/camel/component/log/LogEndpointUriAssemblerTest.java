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
package org.apache.camel.component.log;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.spi.EndpointUriFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class LogEndpointUriAssemblerTest extends ContextTestSupport {

    @Test
    public void testLogAssembler() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("loggerName", "foo");
        params.put("groupSize", "123");
        params.put("showExchangePattern", false);
        params.put("logMask", true);

        // should find the source code generated assembler via classpath
        EndpointUriFactory assembler = context.getCamelContextExtension().getEndpointUriFactory("log");
        Assertions.assertNotNull(assembler);
        boolean generated = assembler instanceof LogEndpointUriFactory;
        Assertions.assertTrue(generated);

        String uri = assembler.buildUri("log", params);
        Assertions.assertEquals("log:foo?groupSize=123&logMask=true&showExchangePattern=false", uri);
    }
}
