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
package org.apache.camel.component.ibm.watsonx.ai;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.ibm.watsonx.ai.timeseries.ForecastData;
import com.ibm.watsonx.ai.timeseries.InputSchema;
import com.ibm.watsonx.ai.timeseries.TimeSeriesParameters;
import com.ibm.watsonx.ai.timeseries.TimeSeriesRequest;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The forecast parameters reach watsonx.ai: the prediction length of the option, the header or a TimeSeriesRequest
 * body, and the future data of deploymentForecast; the schema and the data can be Maps, as a YAML or XML route gives
 * them.
 */
class WatsonxAiForecastTest extends CamelTestSupport {

    private static final String FORECAST_RESPONSE = """
            {"model_id":"ibm/granite-ttm-512-96-r2","created_at":"2026-01-01T00:00:00.000Z",
             "results":[{"date":["2024-01-04T00:00:00"],"value":[12.0]}],"input_data_points":3,"output_data_points":1}""";
    private static final String FORECAST = "/ml/v1/time_series/forecast";
    private static final String DEPLOYMENT_FORECAST = "/ml/v1/deployments/my-deployment/time_series/forecast";

    private static FakeWatsonxServer server;

    @BeforeAll
    static void startServer() throws IOException {
        server = FakeWatsonxServer.start();
    }

    @AfterAll
    static void stopServer() {
        server.close();
    }

    @BeforeEach
    void resetServer() {
        server.reset();
        server.respond(FORECAST, FORECAST_RESPONSE).respond(DEPLOYMENT_FORECAST, FORECAST_RESPONSE);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                getContext().getRegistry().bind("auth", FakeWatsonxServer.TEST_AUTHENTICATOR);
                String common = "authenticator=#auth&baseUrl=" + server.baseUrl() + "&projectId=my-project";
                from("direct:forecast")
                        .to("ibm-watsonx-ai:ts?" + common + "&modelId=ibm/granite-ttm-512-96-r2&operation=forecast");
                from("direct:forecastLength")
                        .to("ibm-watsonx-ai:ts?" + common
                            + "&modelId=ibm/granite-ttm-512-96-r2&operation=forecast&predictionLength=12");
                from("direct:deploymentForecast")
                        .to("ibm-watsonx-ai:ts?" + common + "&deploymentId=my-deployment&operation=deploymentForecast");
            }
        };
    }

    private static InputSchema schema() {
        return InputSchema.builder().timestampColumn("date").addTargetColumn("value").build();
    }

    private static ForecastData data() {
        return ForecastData.create()
                .addAll("date", "2024-01-01T00:00:00", "2024-01-02T00:00:00", "2024-01-03T00:00:00")
                .addAll("value", 10.5, 12.3, 11.8);
    }

    @Test
    void predictionLengthOption() throws Exception {
        Exchange result = template.send("direct:forecastLength", e -> {
            e.getIn().setHeader(WatsonxAiConstants.FORECAST_INPUT_SCHEMA, schema());
            e.getIn().setHeader(WatsonxAiConstants.FORECAST_DATA, data());
        });

        assertNull(result.getException());
        assertInstanceOf(List.class, result.getMessage().getBody());
        JsonNode request = server.lastRequest(FORECAST).json();
        assertEquals(12, request.get("parameters").get("prediction_length").asInt());
        assertEquals("ibm/granite-ttm-512-96-r2", request.get("model_id").asString());
        assertEquals("date", request.get("schema").get("timestamp_column").asString());
    }

    @Test
    void predictionLengthHeaderOverridesTheOption() throws Exception {
        template.send("direct:forecastLength", e -> {
            e.getIn().setHeader(WatsonxAiConstants.FORECAST_INPUT_SCHEMA, schema());
            e.getIn().setHeader(WatsonxAiConstants.FORECAST_DATA, data());
            e.getIn().setHeader(WatsonxAiConstants.FORECAST_PREDICTION_LENGTH, "24");
        });

        assertEquals(24, server.lastRequest(FORECAST).json().get("parameters").get("prediction_length").asInt());
    }

    @Test
    void timeSeriesRequestBodyKeepsItsParameters() throws Exception {
        TimeSeriesRequest body = TimeSeriesRequest.builder()
                .inputSchema(schema())
                .data(data())
                .parameters(TimeSeriesParameters.builder().predictionLength(6).build())
                .build();
        Exchange result = template.send("direct:forecast", e -> e.getIn().setBody(body));

        assertNull(result.getException());
        assertEquals(6, server.lastRequest(FORECAST).json().get("parameters").get("prediction_length").asInt());
    }

    @Test
    void noPredictionLengthSendsNoParameters() throws Exception {
        template.send("direct:forecast", e -> {
            e.getIn().setHeader(WatsonxAiConstants.FORECAST_INPUT_SCHEMA, schema());
            e.getIn().setHeader(WatsonxAiConstants.FORECAST_DATA, data());
        });

        assertFalse(server.lastRequest(FORECAST).json().has("parameters"));
    }

    @Test
    void schemaAndDataAsMaps() throws Exception {
        Exchange result = template.send("direct:forecast", e -> {
            e.getIn().setHeader(WatsonxAiConstants.FORECAST_INPUT_SCHEMA,
                    Map.of("timestamp_column", "date", "id_columns", "series_id", "freq", "1h",
                            "target_columns", List.of("value")));
            e.getIn().setHeader(WatsonxAiConstants.FORECAST_DATA,
                    Map.of("date", List.of("2024-01-01T00:00:00", "2024-01-01T01:00:00"),
                            "series_id", List.of("S1", "S1"), "value", List.of(1.0, 2.0)));
        });

        assertNull(result.getException());
        JsonNode request = server.lastRequest(FORECAST).json();
        assertEquals("date", request.get("schema").get("timestamp_column").asString());
        assertEquals("series_id", request.get("schema").get("id_columns").get(0).asString());
        assertEquals("1h", request.get("schema").get("freq").asString());
        assertEquals("value", request.get("schema").get("target_columns").get(0).asString());
        assertEquals(2, request.get("data").get("value").size());
    }

    @Test
    void deploymentForecastSendsFutureData() throws Exception {
        Exchange result = template.send("direct:deploymentForecast", e -> {
            e.getIn().setHeader(WatsonxAiConstants.FORECAST_INPUT_SCHEMA, schema());
            e.getIn().setHeader(WatsonxAiConstants.FORECAST_DATA, data());
            e.getIn().setHeader(WatsonxAiConstants.FORECAST_PREDICTION_LENGTH, 2);
            e.getIn().setHeader(WatsonxAiConstants.FORECAST_FUTURE_DATA,
                    Map.of("date", List.of("2024-01-04T00:00:00", "2024-01-05T00:00:00"), "price", List.of(3, 4)));
        });

        assertNull(result.getException());
        JsonNode request = server.lastRequest(DEPLOYMENT_FORECAST).json();
        assertEquals(2, request.get("parameters").get("prediction_length").asInt());
        assertEquals(2, request.get("future_data").get("price").size());
    }

    @Test
    void deploymentForecastKeepsTheParametersOfTheBody() throws Exception {
        TimeSeriesRequest body = TimeSeriesRequest.builder()
                .inputSchema(schema())
                .data(data())
                .parameters(TimeSeriesParameters.builder().predictionLength(3)
                        .futureData(ForecastData.create().add("price", 5)).build())
                .build();
        Exchange result = template.send("direct:deploymentForecast", e -> e.getIn().setBody(body));

        assertNull(result.getException());
        JsonNode request = server.lastRequest(DEPLOYMENT_FORECAST).json();
        assertEquals(3, request.get("parameters").get("prediction_length").asInt());
        assertEquals(5, request.get("future_data").get("price").get(0).asInt());
    }

    @Test
    void forecastRefusesFutureData() {
        Exchange result = template.send("direct:forecast", e -> {
            e.getIn().setHeader(WatsonxAiConstants.FORECAST_INPUT_SCHEMA, schema());
            e.getIn().setHeader(WatsonxAiConstants.FORECAST_DATA, data());
            e.getIn().setHeader(WatsonxAiConstants.FORECAST_FUTURE_DATA, Map.of("price", List.of(3)));
        });

        IllegalArgumentException e = assertInstanceOf(IllegalArgumentException.class, result.getException());
        assertTrue(e.getMessage().contains("deploymentForecast only"), e.getMessage());
        assertTrue(server.requests(FORECAST).isEmpty());
    }
}
