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
package org.apache.camel.component.arangodb.integration;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperties;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.apache.camel.component.arangodb.ArangoDbConstants.AQL_QUERY;
import static org.apache.camel.component.arangodb.ArangoDbConstants.AQL_QUERY_BIND_PARAMETERS;
import static org.apache.camel.component.arangodb.ArangoDbConstants.RESULT_CLASS_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledIfSystemProperties({
        @DisabledIfSystemProperty(named = "ci.env.name", matches = "apache.org",
                                  disabledReason = "Apache CI nodes are too resource constrained for this test"),
        @DisabledIfSystemProperty(named = "arangodb.tests.disable", matches = "true",
                                  disabledReason = "Manually disabled tests")
})
public class ArangoCollectionQueryIT extends BaseArangoDb {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:query")
                        .to("arangodb:{{arangodb.testDb}}?operation=AQL_QUERY");
            }
        };
    }

    @Test
    public void findByParameters() {
        TestDocumentEntity test = new TestDocumentEntity("bar", 10);
        collection.insertDocument(test);

        TestDocumentEntity test2 = new TestDocumentEntity("bar");
        collection.insertDocument(test2);

        TestDocumentEntity test3 = new TestDocumentEntity();
        test3.setNumber(10);
        collection.insertDocument(test3);

        String query = "FOR t IN " + COLLECTION_NAME + " FILTER t.foo == @foo AND t.number == @number RETURN t";
        Map<String, Object> bindVars = Map.of("foo", test.getFoo(), "number", test.getNumber());

        Exchange result = template.request("direct:query", exchange -> {
            exchange.getMessage().setHeader(AQL_QUERY, query);
            exchange.getMessage().setHeader(AQL_QUERY_BIND_PARAMETERS, bindVars);
            exchange.getMessage().setHeader(RESULT_CLASS_TYPE, TestDocumentEntity.class);
        });

        assertTrue(result.getMessage().getBody() instanceof Collection);
        Collection<TestDocumentEntity> list = (Collection<TestDocumentEntity>) result.getMessage().getBody();

        assertNotNull(list);
        Optional<TestDocumentEntity> optional = list.stream().findFirst();
        assertTrue(optional.isPresent());
        TestDocumentEntity doc = optional.get();
        assertNotNull(doc);
        assertNotNull(doc.getKey());
        assertNotNull(doc.getRev());
        assertEquals(test.getFoo(), doc.getFoo());
        assertEquals(test.getNumber(), doc.getNumber());
    }

}
