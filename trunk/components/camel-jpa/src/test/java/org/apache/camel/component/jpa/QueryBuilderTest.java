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
package org.apache.camel.component.jpa;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * @version 
 */
public class QueryBuilderTest extends CamelTestSupport {

    @Test
    public void testQueryBuilder() throws Exception {
        QueryBuilder q = QueryBuilder.query("select x from SendEmail x");
        assertNotNull(q);
        assertEquals("Query: select x from SendEmail x", q.toString());
    }

    @Test
    public void testNamedQueryBuilder() throws Exception {
        QueryBuilder q = QueryBuilder.namedQuery("step1");
        assertNotNull(q);
        assertEquals("Named: step1", q.toString());
    }

    @Test
    public void testNativeQueryBuilder() throws Exception {
        QueryBuilder q = QueryBuilder.nativeQuery("select count(*) from SendEmail");
        assertNotNull(q);
        assertEquals("NativeQuery: select count(*) from SendEmail", q.toString());
    }

    @Test
    public void testQueryBuilderWithParameters() throws Exception {
        QueryBuilder q = QueryBuilder.query("select x from SendEmail x where x.id = :a");
        assertNotNull(q);
        q.parameters(1);
        assertEquals("Query: select x from SendEmail x where x.id = :a Parameters: [1]", q.toString());
    }

    @Test
    public void testQueryBuilderWithParametersMap() throws Exception {
        QueryBuilder q = QueryBuilder.query("select x from SendEmail x where x.id = :a");
        assertNotNull(q);
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("a", 1);
        q.parameters(map);
        assertEquals("Query: select x from SendEmail x where x.id = :a Parameters: {a=1}", q.toString());
    }

}
