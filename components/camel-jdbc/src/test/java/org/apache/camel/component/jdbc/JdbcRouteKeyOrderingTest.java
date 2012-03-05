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
package org.apache.camel.component.jdbc;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.junit.Test;

/**
 * Tests that key ordering for the Maps (rows) is preserved.
 */
public class JdbcRouteKeyOrderingTest extends JdbcRouteTest {
    
    @SuppressWarnings("unchecked")
    @Test
    @Override
    public void testJdbcRoutes() throws Exception {
        // first we create our exchange using the endpoint
        Endpoint endpoint = context.getEndpoint("direct:hello");
        
        // repeat the test often enough to make sure preserved ordering is not a fluke
        for (int i = 0; i < 10; i++) {
            Exchange exchange = endpoint.createExchange();
            // then we set the SQL on the in body
            exchange.getIn().setBody("select * from customer order by ID");

            // now we send the exchange to the endpoint, and receives the response from Camel
            Exchange out = template.send(endpoint, exchange);

            // assertions of the response
            assertNotNull(out);
            assertNotNull(out.getOut());
            List<Map<String, Object>> rowList = out.getOut().getBody(List.class);
            assertNotNull("out body could not be converted to a List - was: "
                + out.getOut().getBody(), rowList);
            assertEquals(3, rowList.size());
            
            Map<String, Object> row = rowList.get(0);
            assertTrue("ordering not preserved " + row.keySet(), isOrdered(row.keySet()));
            
            row = rowList.get(1);
            assertTrue("ordering not preserved " + row.keySet(), isOrdered(row.keySet()));
        }
    }

    /**
     * @param keySet (should have 2 items "ID" & "NAME")
     * @return true if "ID" comes before "NAME", false otherwise
     */
    private static boolean isOrdered(Set<String> keySet) {
        final String msg = "isOrdered() relies on \"ID\" & \"NAME\" being the only two fields";
        assertTrue(msg, keySet.contains("ID"));
        assertTrue(msg, keySet.contains("NAME"));
        assertEquals(msg, 2, keySet.size());
        
        final Iterator<String> iter = keySet.iterator();
        return "ID".equals(iter.next()) && "NAME".equals(iter.next());
    }
}