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
package org.apache.camel.component.ibm.watsonx.ai.handler;

import java.util.HashMap;
import java.util.Map;

import com.ibm.watsonx.ai.timeseries.ForecastData;
import com.ibm.watsonx.ai.timeseries.ForecastResponse;
import com.ibm.watsonx.ai.timeseries.InputSchema;
import com.ibm.watsonx.ai.timeseries.TimeSeriesRequest;
import com.ibm.watsonx.ai.timeseries.TimeSeriesService;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiConstants;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiEndpoint;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiOperations;

/**
 * Handler for forecast (time series) operations.
 */
public class ForecastHandler extends AbstractWatsonxAiHandler {

    public ForecastHandler(WatsonxAiEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public WatsonxAiOperationResponse handle(Exchange exchange, WatsonxAiOperations operation) throws Exception {
        if (operation != WatsonxAiOperations.forecast) {
            throw new IllegalArgumentException("Unsupported operation: " + operation);
        }
        return processForecast(exchange);
    }

    @Override
    public WatsonxAiOperations[] getSupportedOperations() {
        return new WatsonxAiOperations[] { WatsonxAiOperations.forecast };
    }

    private WatsonxAiOperationResponse processForecast(Exchange exchange) {
        Message in = exchange.getIn();

        // Get input schema and data from headers or body
        InputSchema inputSchema = in.getHeader(WatsonxAiConstants.FORECAST_INPUT_SCHEMA, InputSchema.class);
        ForecastData forecastData = in.getHeader(WatsonxAiConstants.FORECAST_DATA, ForecastData.class);

        // If not in headers, try to get from body as TimeSeriesRequest
        if (inputSchema == null || forecastData == null) {
            Object body = in.getBody();
            if (body instanceof TimeSeriesRequest request) {
                inputSchema = request.inputSchema();
                forecastData = request.data();
            }
        }

        if (inputSchema == null) {
            throw new IllegalArgumentException(
                    "Input schema must be provided via header '" + WatsonxAiConstants.FORECAST_INPUT_SCHEMA
                                               + "' or as part of TimeSeriesRequest in body");
        }
        if (forecastData == null) {
            throw new IllegalArgumentException(
                    "Forecast data must be provided via header '" + WatsonxAiConstants.FORECAST_DATA
                                               + "' or as part of TimeSeriesRequest in body");
        }

        // Call the service
        TimeSeriesService service = endpoint.getTimeSeriesService();
        ForecastResponse response = service.forecast(inputSchema, forecastData);

        // Build response headers
        Map<String, Object> headers = new HashMap<>();
        headers.put(WatsonxAiConstants.FORECAST_RESULTS, response.results());
        headers.put(WatsonxAiConstants.FORECAST_INPUT_DATA_POINTS, response.inputDataPoints());
        headers.put(WatsonxAiConstants.FORECAST_OUTPUT_DATA_POINTS, response.outputDataPoints());
        headers.put(WatsonxAiConstants.MODEL_ID, response.modelId());

        return WatsonxAiOperationResponse.create(response.results(), headers);
    }
}
