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
package org.apache.camel.dataformat.csv.component;

import org.apache.camel.CamelContext;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.main.Main;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies that a {@code dataformat:} component endpoint honors the global {@code camel.dataformat.csv.*} configuration
 * (here {@code ignore-surrounding-spaces=true}) the same way the {@code unmarshal()} DSL does (CAMEL-23772).
 */
public class DataFormatComponentConfigTest {

    @Test
    public void testGlobalConfigAppliedToDataFormatComponent() throws Exception {
        Main main = new Main();
        main.configure().withBasePackageScan("org.apache.camel.dataformat.csv.component");
        main.start();
        try {
            CamelContext camelContext = main.getCamelContext();
            assertNotNull(camelContext);
            assertEquals(1, camelContext.getRoutes().size());

            MockEndpoint mock = camelContext.getEndpoint("mock:result", MockEndpoint.class);
            mock.expectedMessageCount(1);
            mock.assertIsSatisfied();

            Object body = mock.getReceivedExchanges().get(0).getMessage().getBody();
            assertNotNull(body);
            // global camel.dataformat.csv.ignore-surrounding-spaces=true must be applied, so the
            // surrounding spaces are stripped; without the fix the component ignores it and the
            // spaces would be preserved
            assertEquals("[[One], [Two], [Three]]", body.toString());
        } finally {
            main.stop();
        }
    }
}
