package org.apache.camel.component.graphql;

import static org.junit.Assert.assertEquals;

import org.apache.camel.util.json.JsonObject;
import org.junit.Test;

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
