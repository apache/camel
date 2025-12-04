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

package org.apache.camel.dataformat.csv.main;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.camel.CamelContext;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.main.Main;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MainCsvTest {

    @Test
    public void testMainCsv() throws Exception {
        Main main = new Main();
        main.configure().withBasePackageScan("org.apache.camel.dataformat.csv.main");
        main.start();

        CamelContext camelContext = main.getCamelContext();
        assertNotNull(camelContext);
        assertEquals(2, camelContext.getRoutes().size());

        MockEndpoint mock = camelContext.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedMessageCount(1);
        mock.assertIsSatisfied();
        Object body = mock.getReceivedExchanges().get(0).getMessage().getBody();
        assertNotNull(body);
        // should ignore surrounding spaces
        Assertions.assertEquals("[[One], [Two], [Three]]", body.toString());

        mock = camelContext.getEndpoint("mock:result2", MockEndpoint.class);
        mock.expectedMessageCount(1);
        mock.assertIsSatisfied();
        body = mock.getReceivedExchanges().get(0).getMessage().getBody();
        assertNotNull(body);
        // should ignore surrounding spaces
        Assertions.assertEquals("[[Four, Five, Six]]", body.toString());

        main.stop();
    }
}
