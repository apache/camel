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
package org.apache.camel.component.aws.cw;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.MetricDatum;
import com.amazonaws.services.cloudwatch.model.PutMetricDataRequest;
import com.amazonaws.services.cloudwatch.model.StandardUnit;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class CwComponentTest extends CamelTestSupport {
    private static final Date NOW = new Date();
    private static final Date LATER = new Date(NOW.getTime() + 1);
    private AmazonCloudWatchClient cloudWatchClient = mock(AmazonCloudWatchClient.class);

    @Test
    public void sendMetricFromHeaderValues() throws Exception {
        template.send("direct:start", ExchangePattern.InOnly, new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(CwConstants.METRIC_NAMESPACE, "camel.apache.org/overriden");
                exchange.getIn().setHeader(CwConstants.METRIC_NAME, "OverridenMetric");
                exchange.getIn().setHeader(CwConstants.METRIC_VALUE, Double.valueOf(3));
                exchange.getIn().setHeader(CwConstants.METRIC_UNIT, StandardUnit.Bytes.toString());
                exchange.getIn().setHeader(CwConstants.METRIC_TIMESTAMP, LATER);
            }
        });


        ArgumentCaptor<PutMetricDataRequest> argument = ArgumentCaptor.forClass(PutMetricDataRequest.class);
        verify(cloudWatchClient).putMetricData(argument.capture());

        assertEquals("camel.apache.org/overriden", argument.getValue().getNamespace());
        assertEquals("OverridenMetric", argument.getValue().getMetricData().get(0).getMetricName());
        assertEquals(Double.valueOf(3), argument.getValue().getMetricData().get(0).getValue());
        assertEquals(StandardUnit.Bytes.toString(), argument.getValue().getMetricData().get(0).getUnit());
        assertEquals(LATER, argument.getValue().getMetricData().get(0).getTimestamp());
    }

    @Test
    public void sendManuallyCreatedMetric() throws Exception {
        template.send("direct:start", ExchangePattern.InOnly, new Processor() {
            public void process(Exchange exchange) throws Exception {
                MetricDatum metricDatum = new MetricDatum()
                        .withMetricName("errorCount")
                        .withValue(Double.valueOf(0));
                exchange.getIn().setBody(metricDatum);
            }
        });


        ArgumentCaptor<PutMetricDataRequest> argument = ArgumentCaptor.forClass(PutMetricDataRequest.class);
        verify(cloudWatchClient).putMetricData(argument.capture());

        assertEquals("errorCount", argument.getValue().getMetricData().get(0).getMetricName());
        assertEquals(Double.valueOf(0), argument.getValue().getMetricData().get(0).getValue());
    }

    @Test
    public void useDefaultValuesForMetricUnitAndMetricValue() throws Exception {
        template.send("direct:start", ExchangePattern.InOnly, new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(CwConstants.METRIC_NAME, "errorCount");
            }
        });


        ArgumentCaptor<PutMetricDataRequest> argument = ArgumentCaptor.forClass(PutMetricDataRequest.class);
        verify(cloudWatchClient).putMetricData(argument.capture());

        assertEquals("errorCount", argument.getValue().getMetricData().get(0).getMetricName());
        assertEquals(Double.valueOf(1), argument.getValue().getMetricData().get(0).getValue());
        assertEquals(StandardUnit.Count.toString(), argument.getValue().getMetricData().get(0).getUnit());
    }

    @Test
    public void setsMeticDimensions() throws Exception {
        template.send("direct:start", ExchangePattern.InOnly, new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(CwConstants.METRIC_NAME, "errorCount");
                Map<String, String> dimensionsMap = new LinkedHashMap<String, String>();
                dimensionsMap.put("keyOne", "valueOne");
                dimensionsMap.put("keyTwo", "valueTwo");
                exchange.getIn().setHeader(CwConstants.METRIC_DIMENSIONS, dimensionsMap);
            }
        });

        ArgumentCaptor<PutMetricDataRequest> argument = ArgumentCaptor.forClass(PutMetricDataRequest.class);
        verify(cloudWatchClient).putMetricData(argument.capture());

        List<Dimension> dimensions = argument.getValue().getMetricData().get(0).getDimensions();
        Dimension dimension = dimensions.get(0);
        assertThat(dimensions.size(), is(2));
        assertEquals("keyOne", dimension.getName());
        assertEquals("valueOne", dimension.getValue());
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        registry.bind("amazonCwClient", cloudWatchClient);
        registry.bind("now", NOW);
        return registry;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .to("aws-cw://camel.apache.org/test?amazonCwClient=#amazonCwClient&name=testMetric&unit=Count&timestamp=#now");
            }
        };
    }
}
