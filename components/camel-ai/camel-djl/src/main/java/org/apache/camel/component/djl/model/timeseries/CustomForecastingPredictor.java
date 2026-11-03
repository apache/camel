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
package org.apache.camel.component.djl.model.timeseries;

import ai.djl.inference.Predictor;
import ai.djl.timeseries.Forecast;
import ai.djl.timeseries.TimeSeriesData;
import ai.djl.translate.TranslateException;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.djl.DJLConstants;
import org.apache.camel.component.djl.DJLEndpoint;
import org.apache.camel.component.djl.model.AbstractCustomPredictor;

public class CustomForecastingPredictor extends AbstractCustomPredictor<TimeSeriesData, Forecast> {

    public CustomForecastingPredictor(DJLEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        if (exchange.getIn().getBody() instanceof TimeSeriesData) {
            TimeSeriesData input = exchange.getIn().getBody(TimeSeriesData.class);
            Forecast result = predict(exchange, input);
            exchange.getIn().setBody(result);
        } else {
            throw new RuntimeCamelException("Data type is not supported. Body should be TimeSeriesData");
        }
    }

    protected Forecast predict(Exchange exchange, TimeSeriesData input) {
        exchange.getIn().setHeader(DJLConstants.INPUT, input);
        try (Predictor<TimeSeriesData, Forecast> predictor = newPredictor()) {
            return predictor.predict(input);
        } catch (TranslateException e) {
            throw new RuntimeCamelException("Could not process input or output", e);
        }
    }
}
