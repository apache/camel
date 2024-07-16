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
package org.apache.camel.component.tahu;

import java.util.Arrays;
import java.util.Date;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.TypeConverter;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.spi.TypeConverterRegistry;
import org.eclipse.tahu.SparkplugInvalidTypeException;
import org.eclipse.tahu.message.model.Metric;
import org.eclipse.tahu.message.model.MetricDataType;
import org.eclipse.tahu.message.model.SparkplugBPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Converter(generateLoader = true)
public class TahuEdgePayloadConverter implements HeaderFilterStrategyAware {

    private static final Logger LOG = LoggerFactory.getLogger(TahuEdgePayloadConverter.class);

    @Converter
    public SparkplugBPayload exchangeToSparkplugBPayload(Exchange exchange) {
        LOG.debug("Called exchangeToSparkplugBPayload");
        return messageToSparkplugBPayload(exchange.getMessage(), exchange);
    }

    @Converter
    public SparkplugBPayload messageToSparkplugBPayload(Message message, Exchange exchange) {

        Message messageCopy = message.copy();

        SparkplugBPayload.SparkplugBPayloadBuilder dataPayloadBuilder = new SparkplugBPayload.SparkplugBPayloadBuilder();

        final Date payloadDate = getPayloadDate(exchange, messageCopy, dataPayloadBuilder);
        dataPayloadBuilder.setTimestamp(payloadDate);

        Object body = messageCopy.getBody();
        if (body != null && body != message) {
            TypeConverterRegistry registry = exchange.getContext().getTypeConverterRegistry();

            TypeConverter converter = registry.lookup(byte[].class, body.getClass());
            if (converter != null) {
                byte[] valueBytes = converter.convertTo(byte[].class, body);

                dataPayloadBuilder.setBody(valueBytes);
            }
        }

        messageCopy.getHeaders().forEach((headerName, headerValue) -> {
            if (skipMetricHeader(headerName, headerValue, exchange)) {

                // Skip headers where the headerFilterStrategy returns true, per
                // HeaderFilterStrategy.applyFilterToCamelHeaders

            } else if (headerName.equals(TahuConstants.MESSAGE_UUID)) {

                dataPayloadBuilder.setUuid(headerValue.toString());

            } else if (headerValue instanceof Metric) {
                Metric metricValue = (Metric) headerValue;

                dataPayloadBuilder.addMetric(metricValue);

            } else if (headerName.startsWith(TahuConstants.METRIC_HEADER_PREFIX)) {

                String metricName = headerName;

                // If using the default headerFilterStrategy, strip off the header name prefix
                if (metricName.startsWith(TahuConstants.METRIC_HEADER_PREFIX)) {
                    metricName = metricName.substring(TahuConstants.METRIC_HEADER_PREFIX.length());
                }

                try {
                    MetricDataType defaultType = getDefaultMetricDataType(headerValue);
                    Metric metric = new Metric.MetricBuilder(metricName, defaultType, headerValue).createMetric();
                    metric.setTimestamp(payloadDate);

                    dataPayloadBuilder.addMetric(metric);
                } catch (SparkplugInvalidTypeException site) {
                    exchange.setException(site);
                }
            }
        });

        return dataPayloadBuilder.createPayload();

    }

    private Date getPayloadDate(
            Exchange exchange, Message messageCopy,
            SparkplugBPayload.SparkplugBPayloadBuilder dataPayloadBuilder) {
        long payloadTimestamp;
        if (messageCopy != null && messageCopy.getMessageTimestamp() != 0L) {
            payloadTimestamp = messageCopy.getMessageTimestamp();
        } else if (exchange.getClock().getCreated() != 0L) {
            payloadTimestamp = exchange.getClock().getCreated();
        } else {
            payloadTimestamp = System.currentTimeMillis();
        }
        return new Date(payloadTimestamp);
    }

    private static class MetricDataTypeStreamHolder {
        private static int[] VALUES;
        static {
            // 20 and 21 are not MetricDataTypes, 35 will hit the default case to include
            // MetricDataType.Unknown
            VALUES = IntStream.range(1, 35)
                    .filter(i -> i != 20 && i != 21)
                    .toArray();
        }

        private static Stream<MetricDataType> getStream() {
            return Arrays.stream(VALUES).mapToObj(MetricDataType::fromInteger);
        }
    }

    private static MetricDataType getDefaultMetricDataType(Object headerValue) {
        if (headerValue == null) {
            return MetricDataType.Unknown;
        }

        MetricDataType defaultType = MetricDataTypeStreamHolder.getStream()
                .dropWhile(checkType -> !checkType.getClazz().isAssignableFrom(headerValue.getClass()))
                .findFirst().orElse(MetricDataType.Unknown);

        return defaultType;
    }

    private boolean skipMetricHeader(String headerName, Object headerValue, Exchange exchange) {
        return headerFilterStrategy != null
                && headerFilterStrategy.applyFilterToCamelHeaders(headerName, headerValue, exchange);
    }

    private HeaderFilterStrategy headerFilterStrategy;

    @Override
    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    @Override
    public void setHeaderFilterStrategy(HeaderFilterStrategy strategy) {
        this.headerFilterStrategy = strategy;
    }
}
