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
package org.apache.camel.component.graphql;

import org.apache.camel.util.json.JsonObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GraphqlProducerTest {

    @Test
    public void shouldBuildRequestBodyWithQuery() {
        String query = "queryText";

        String body = GraphqlProducer.buildRequestBody(query, null, null);

        String expectedBody = "{"
            + "\"query\":\"queryText\","
            + "\"operationName\":null,"
            + "\"variables\":{}"
            + "}";
        assertEquals(expectedBody, body);
    }

    @Test
    public void shouldBuildRequestBodyWithQueryOperationNameAndVariables() {
        String query = "queryText";
        String operationName = "queryName";
        JsonObject variables = new JsonObject();
        variables.put("key1", "value1");
        variables.put("key2", "value2");

        String body = GraphqlProducer.buildRequestBody(query, operationName, variables);

        String expectedBody = "{"
            + "\"query\":\"queryText\","
            + "\"operationName\":\"queryName\","
            + "\"variables\":{\"key1\":\"value1\",\"key2\":\"value2\"}"
            + "}";
        assertEquals(expectedBody, body);
    }

}
