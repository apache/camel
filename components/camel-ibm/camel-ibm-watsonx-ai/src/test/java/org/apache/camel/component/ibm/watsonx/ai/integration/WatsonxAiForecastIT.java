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
package org.apache.camel.component.ibm.watsonx.ai.integration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.ibm.watsonx.ai.timeseries.ForecastData;
import com.ibm.watsonx.ai.timeseries.InputSchema;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for watsonx.ai time series forecast operations.
 */
@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "camel.ibm.watsonx.ai.apiKey", matches = ".+",
                                 disabledReason = "IBM watsonx.ai API Key not provided"),
        @EnabledIfSystemProperty(named = "camel.ibm.watsonx.ai.projectId", matches = ".+",
                                 disabledReason = "IBM watsonx.ai Project ID not provided")
})
public class WatsonxAiForecastIT extends WatsonxAiTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(WatsonxAiForecastIT.class);
    private static final String FORECAST_MODEL = "ibm/granite-ttm-1536-96-r2";

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint mockResult;

    @BeforeEach
    public void resetMocks() {
        mockResult.reset();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testForecast() throws Exception {
        mockResult.expectedMessageCount(1);

        // Create input schema
        InputSchema schema = InputSchema.builder()
                .timestampColumn("date")
                .addIdColumn("series_id")
                .build();

        // The model ibm/granite-ttm-1536-96-r2 requires at least 1536 time points
        // Generate sufficient data points for the model
        final int dataPoints = 1536;
        ForecastData data = ForecastData.create();

        LocalDateTime startDate = LocalDateTime.of(2024, 1, 1, 0, 0, 0);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'+00:00'");

        for (int i = 0; i < dataPoints; i++) {
            data.add("date", startDate.plusHours(i).format(formatter));
            data.add("series_id", "S1");
            // Generate a simple sine wave pattern with some noise
            double value = 100 + 20 * Math.sin(2 * Math.PI * i / 24) + Math.random() * 5;
            data.add("value", value);
        }

        LOG.info("Generated {} data points for forecast test", dataPoints);

        template.send("direct:forecast", exchange -> {
            exchange.getIn().setHeader(WatsonxAiConstants.FORECAST_INPUT_SCHEMA, schema);
            exchange.getIn().setHeader(WatsonxAiConstants.FORECAST_DATA, data);
        });

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        Object body = exchange.getIn().getBody();

        assertNotNull(body, "Response body should not be null");
        assertTrue(body instanceof List, "Response should be a list");

        List<?> results = (List<?>) body;
        LOG.info("Forecast results count: {}", results.size());

        // Verify metadata headers
        String modelId = exchange.getIn().getHeader(WatsonxAiConstants.MODEL_ID, String.class);
        assertNotNull(modelId, "Model ID header should be set");
        LOG.info("Model used: {}", modelId);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:forecast")
                        .to(buildEndpointUri("forecast", FORECAST_MODEL))
                        .to("mock:result");
            }
        };
    }
}
