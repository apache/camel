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
package org.apache.camel.component.djl;

import java.time.LocalDateTime;

import ai.djl.ndarray.NDManager;
import ai.djl.timeseries.TimeSeriesData;
import ai.djl.timeseries.dataset.FieldName;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class TimeSeriesForecastingTest extends CamelTestSupport {

    @BeforeAll
    public static void setupDefaultEngine() {
        // Since Apache MXNet is discontinued, prefer PyTorch as the default engine
        System.setProperty("ai.djl.default_engine", "PyTorch");
    }

    @Test
    void testDJL() throws Exception {
        var mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.await();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("timer:testDJL?repeatCount=1")
                        .process(exchange -> {
                            var input = new TimeSeriesData(10);
                            input.setStartTime(LocalDateTime.now());
                            var manager = NDManager.newBaseManager("PyTorch");
                            var data = manager.create(new float[] {
                                    1.0f, 2.0f, 1.0f, 2.0f, 3.0f,
                                    1.0f, 2.0f, 3.0f, 4.0f, 1.0f });
                            input.setField(FieldName.TARGET, data);
                            exchange.getIn().setBody(input);
                            exchange.getIn().setHeader("NDManager", manager);
                        })
                        .doTry()
                        .to("djl:timeseries/forecasting?artifactId=ai.djl.pytorch:deepar:0.0.1")
                        .log("Mean: ${body.mean}")
                        .log("Median: ${body.median}")
                        .doFinally()
                        .process(exchange -> {
                            var manager = exchange.getIn().getHeader("NDManager", NDManager.class);
                            manager.close();
                        })
                        .end()
                        .to("mock:result");
            }
        };
    }

}
