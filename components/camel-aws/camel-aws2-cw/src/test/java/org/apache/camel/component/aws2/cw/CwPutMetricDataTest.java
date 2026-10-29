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
package org.apache.camel.component.aws2.cw;

import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CwPutMetricDataTest extends CamelTestSupport {

    private static final Instant OPTION_TIMESTAMP = Instant.ofEpochSecond(1000);

    @BindToRegistry("optionTimestamp")
    private final Instant optionTimestamp = OPTION_TIMESTAMP;

    @BindToRegistry("amazonCwClient")
    private final CloudWatchClientMock cloudWatchClient = new CloudWatchClientMock();

    private static MetricDatum datum(String name) {
        return MetricDatum.builder().metricName(name).value(1d).unit(StandardUnit.COUNT).build();
    }

    private List<MetricDatum> sentMetricData() {
        return cloudWatchClient.getLastPutMetricDataRequest().metricData();
    }

    @Test
    public void sendSetOfMetricDatum() {
        Set<MetricDatum> body = new LinkedHashSet<>(List.of(datum("first"), datum("second")));
        template.sendBody("direct:start", body);

        List<MetricDatum> sent = sentMetricData();
        assertEquals(2, sent.size());
        assertEquals("first", sent.get(0).metricName());
        assertEquals("second", sent.get(1).metricName());
    }

    @Test
    public void sendArrayOfMetricDatum() {
        MetricDatum[] body = new MetricDatum[] { datum("first"), datum("second") };
        template.sendBody("direct:start", body);

        List<MetricDatum> sent = sentMetricData();
        assertEquals(2, sent.size());
        assertEquals("first", sent.get(0).metricName());
        assertEquals("second", sent.get(1).metricName());
    }

    @Test
    public void sendListOfMetricDatum() {
        template.sendBody("direct:start", List.of(datum("first")));

        List<MetricDatum> sent = sentMetricData();
        assertEquals(1, sent.size());
        assertEquals("first", sent.get(0).metricName());
    }

    @Test
    public void listOfOtherElementsFails() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:start", List.of(datum("first"), "second")));
        IllegalArgumentException cause = assertInstanceOf(IllegalArgumentException.class, e.getCause());
        assertTrue(cause.getMessage().contains("must be a MetricDatum or a collection or an array of MetricDatum"),
                cause.getMessage());
        assertTrue(cause.getMessage().contains("java.lang.String"), cause.getMessage());
    }

    @Test
    public void otherBodyTakesMetricFromHeadersAndOptions() {
        template.sendBodyAndHeader("direct:start", "some text", Cw2Constants.METRIC_VALUE, 5d);

        List<MetricDatum> sent = sentMetricData();
        assertEquals(1, sent.size());
        assertEquals("testMetric", sent.get(0).metricName());
        assertEquals(5d, sent.get(0).value());
    }

    @Test
    public void dateTimestampHeader() {
        Date date = new Date(2000000L);
        template.sendBodyAndHeader("direct:start", null, Cw2Constants.METRIC_TIMESTAMP, date);

        assertEquals(date.toInstant(), sentMetricData().get(0).timestamp());
    }

    @Test
    public void instantTimestampHeader() {
        Instant instant = Instant.ofEpochSecond(3000);
        template.sendBodyAndHeader("direct:start", null, Cw2Constants.METRIC_TIMESTAMP, instant);

        assertEquals(instant, sentMetricData().get(0).timestamp());
    }

    @Test
    public void timestampOption() {
        template.sendBody("direct:start", null);

        assertEquals(OPTION_TIMESTAMP, sentMetricData().get(0).timestamp());
    }

    @Test
    public void upperCaseUnitOption() {
        template.sendBody("direct:start", null);

        assertEquals(StandardUnit.BYTES, sentMetricData().get(0).unit());
    }

    @Test
    public void unitHeaderInAnyCase() {
        template.sendBodyAndHeader("direct:start", null, Cw2Constants.METRIC_UNIT, "kilobytes/second");

        assertEquals(StandardUnit.KILOBYTES_SECOND, sentMetricData().get(0).unit());
    }

    @Test
    public void unknownUnitFails() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBodyAndHeader("direct:start", null, Cw2Constants.METRIC_UNIT, "Bananas"));
        IllegalArgumentException cause = assertInstanceOf(IllegalArgumentException.class, e.getCause());
        assertTrue(cause.getMessage().contains("Unknown unit Bananas"), cause.getMessage());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to(
                        "aws2-cw://camel.apache.org/test?amazonCwClient=#amazonCwClient&name=testMetric&unit=BYTES&timestamp=#optionTimestamp");
            }
        };
    }
}
