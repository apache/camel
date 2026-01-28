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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.health.WritableHealthCheckRepository;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.DescribeAlarmsForMetricRequest;
import software.amazon.awssdk.services.cloudwatch.model.DescribeAlarmsForMetricResponse;
import software.amazon.awssdk.services.cloudwatch.model.DescribeAlarmsRequest;
import software.amazon.awssdk.services.cloudwatch.model.DescribeAlarmsResponse;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.DimensionFilter;
import software.amazon.awssdk.services.cloudwatch.model.ListMetricsRequest;
import software.amazon.awssdk.services.cloudwatch.model.ListMetricsResponse;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;

/**
 * A Producer which sends messages to the AWS CloudWatch Service
 */
public class Cw2Producer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(Cw2Producer.class);

    private HealthCheck producerHealthCheck;
    private WritableHealthCheckRepository healthCheckRepository;
    private transient String cwProducerToString;

    public Cw2Producer(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Cw2Operations operation = determineOperation(exchange);
        CloudWatchClient client = getEndpoint().getCloudWatchClient();

        switch (operation) {
            case putMetricData:
                putMetricData(client, exchange);
                break;
            case listMetrics:
                listMetrics(client, exchange);
                break;
            case describeAlarms:
                describeAlarms(client, exchange);
                break;
            case describeAlarmsForMetric:
                describeAlarmsForMetric(client, exchange);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation: " + operation);
        }
    }

    private Cw2Operations determineOperation(Exchange exchange) {
        Cw2Operations operation = exchange.getIn().getHeader(Cw2Constants.OPERATION, Cw2Operations.class);
        if (operation == null) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }

    private void putMetricData(CloudWatchClient client, Exchange exchange) {
        List<MetricDatum> metricData = getMetricData(exchange);

        PutMetricDataRequest.Builder builder = PutMetricDataRequest.builder();
        builder.metricData(metricData).namespace(determineNameSpace(exchange));

        PutMetricDataRequest request = builder.build();
        LOG.info("Sending request [{}] from exchange [{}]...", request, exchange);
        client.putMetricData(request);
    }

    private void listMetrics(CloudWatchClient client, Exchange exchange) {
        ListMetricsRequest.Builder builder = ListMetricsRequest.builder();

        String namespace = getOptionalHeader(exchange, Cw2Constants.METRIC_NAMESPACE, String.class,
                () -> getConfiguration().getNamespace(), "namespace");
        if (ObjectHelper.isNotEmpty(namespace)) {
            builder.namespace(namespace);
        }

        String metricName = getOptionalHeader(exchange, Cw2Constants.METRIC_NAME, String.class,
                () -> getConfiguration().getName(), "metric name");
        if (ObjectHelper.isNotEmpty(metricName)) {
            builder.metricName(metricName);
        }

        String nextToken = exchange.getIn().getHeader(Cw2Constants.NEXT_TOKEN, String.class);
        if (ObjectHelper.isNotEmpty(nextToken)) {
            builder.nextToken(nextToken);
        }

        // Handle dimensions filter
        @SuppressWarnings("unchecked")
        Map<String, String> dimensions = exchange.getIn().getHeader(Cw2Constants.METRIC_DIMENSIONS, Map.class);
        if (ObjectHelper.isNotEmpty(dimensions)) {
            List<DimensionFilter> dimensionFilters = new ArrayList<>();
            for (Map.Entry<String, String> entry : dimensions.entrySet()) {
                dimensionFilters.add(DimensionFilter.builder()
                        .name(entry.getKey())
                        .value(entry.getValue())
                        .build());
            }
            builder.dimensions(dimensionFilters);
        }

        ListMetricsResponse response = client.listMetrics(builder.build());

        Message message = getMessageForResponse(exchange);
        message.setBody(response);
        message.setHeader(Cw2Constants.NEXT_TOKEN, response.nextToken());
        message.setHeader(Cw2Constants.IS_TRUNCATED, ObjectHelper.isNotEmpty(response.nextToken()));
    }

    private void describeAlarms(CloudWatchClient client, Exchange exchange) {
        DescribeAlarmsRequest.Builder builder = DescribeAlarmsRequest.builder();

        String alarmName = exchange.getIn().getHeader(Cw2Constants.ALARM_NAME, String.class);
        if (ObjectHelper.isNotEmpty(alarmName)) {
            builder.alarmNames(alarmName);
        }

        String stateValue = exchange.getIn().getHeader(Cw2Constants.ALARM_STATE, String.class);
        if (ObjectHelper.isNotEmpty(stateValue)) {
            builder.stateValue(stateValue);
        }

        String nextToken = exchange.getIn().getHeader(Cw2Constants.NEXT_TOKEN, String.class);
        if (ObjectHelper.isNotEmpty(nextToken)) {
            builder.nextToken(nextToken);
        }

        Integer maxResults = exchange.getIn().getHeader(Cw2Constants.MAX_RESULTS, Integer.class);
        if (ObjectHelper.isNotEmpty(maxResults)) {
            builder.maxRecords(maxResults);
        }

        DescribeAlarmsResponse response = client.describeAlarms(builder.build());

        Message message = getMessageForResponse(exchange);
        message.setBody(response);
        message.setHeader(Cw2Constants.NEXT_TOKEN, response.nextToken());
        message.setHeader(Cw2Constants.IS_TRUNCATED, ObjectHelper.isNotEmpty(response.nextToken()));
    }

    private void describeAlarmsForMetric(CloudWatchClient client, Exchange exchange) {
        DescribeAlarmsForMetricRequest.Builder builder = DescribeAlarmsForMetricRequest.builder();

        String namespace = getRequiredHeader(exchange, Cw2Constants.METRIC_NAMESPACE, String.class,
                () -> getConfiguration().getNamespace(), "Namespace is required for describeAlarmsForMetric");
        builder.namespace(namespace);

        String metricName = getRequiredHeader(exchange, Cw2Constants.METRIC_NAME, String.class,
                () -> getConfiguration().getName(), "Metric name is required for describeAlarmsForMetric");
        builder.metricName(metricName);

        // Handle dimensions
        @SuppressWarnings("unchecked")
        Map<String, String> dimensions = exchange.getIn().getHeader(Cw2Constants.METRIC_DIMENSIONS, Map.class);
        if (ObjectHelper.isNotEmpty(dimensions)) {
            List<Dimension> dimensionList = new ArrayList<>();
            for (Map.Entry<String, String> entry : dimensions.entrySet()) {
                dimensionList.add(Dimension.builder()
                        .name(entry.getKey())
                        .value(entry.getValue())
                        .build());
            }
            builder.dimensions(dimensionList);
        }

        DescribeAlarmsForMetricResponse response = client.describeAlarmsForMetric(builder.build());

        Message message = getMessageForResponse(exchange);
        message.setBody(response);
    }

    private List<MetricDatum> getMetricData(Exchange exchange) {
        Object body = exchange.getIn().getBody();
        if (body instanceof List) {
            return CastUtils.cast((List<?>) body);
        }

        if (body instanceof MetricDatum) {
            return Arrays.asList((MetricDatum) body);
        }

        MetricDatum.Builder metricDatum = MetricDatum.builder().metricName(determineName(exchange))
                .value(determineValue(exchange)).unit(determineUnit(exchange))
                .timestamp(determineTimestamp(exchange));
        setDimension(metricDatum, exchange);
        return Arrays.asList(metricDatum.build());
    }

    private void setDimension(MetricDatum.Builder metricDatum, Exchange exchange) {
        String name = exchange.getIn().getHeader(Cw2Constants.METRIC_DIMENSION_NAME, String.class);
        String value = exchange.getIn().getHeader(Cw2Constants.METRIC_DIMENSION_VALUE, String.class);
        if (ObjectHelper.isNotEmpty(name) && ObjectHelper.isNotEmpty(value)) {
            metricDatum.dimensions(Dimension.builder().name(name).value(value).build());
        } else {
            @SuppressWarnings("unchecked")
            Map<String, String> dimensions = exchange.getIn().getHeader(Cw2Constants.METRIC_DIMENSIONS, Map.class);
            if (ObjectHelper.isNotEmpty(dimensions)) {
                Collection<Dimension> dimensionCollection = new ArrayList<>();
                for (Map.Entry<String, String> dimensionEntry : dimensions.entrySet()) {
                    Dimension dimension
                            = Dimension.builder().name(dimensionEntry.getKey()).value(dimensionEntry.getValue()).build();
                    dimensionCollection.add(dimension);
                }
                metricDatum.dimensions(dimensionCollection);
            }
        }
    }

    private Instant determineTimestamp(Exchange exchange) {
        return getOptionalHeader(exchange, Cw2Constants.METRIC_TIMESTAMP, Instant.class,
                () -> getConfiguration().getTimestamp(), "timestamp");
    }

    private String determineNameSpace(Exchange exchange) {
        return getOptionalHeader(exchange, Cw2Constants.METRIC_NAMESPACE, String.class,
                () -> getConfiguration().getNamespace(), "namespace");
    }

    private String determineName(Exchange exchange) {
        return getOptionalHeader(exchange, Cw2Constants.METRIC_NAME, String.class,
                () -> getConfiguration().getName(), "name");
    }

    private Double determineValue(Exchange exchange) {
        Double value = getOptionalHeader(exchange, Cw2Constants.METRIC_VALUE, Double.class,
                () -> getConfiguration().getValue(), "value");
        return ObjectHelper.isNotEmpty(value) ? value : Double.valueOf(1);
    }

    private StandardUnit determineUnit(Exchange exchange) {
        String unit = getOptionalHeader(exchange, Cw2Constants.METRIC_UNIT, String.class,
                () -> getConfiguration().getUnit(), "unit");
        return ObjectHelper.isNotEmpty(unit) ? StandardUnit.fromValue(unit) : StandardUnit.COUNT;
    }

    /**
     * Gets an optional value from the exchange header, falling back to configuration if not present.
     */
    private <T> T getOptionalHeader(
            Exchange exchange,
            String headerName,
            Class<T> headerType,
            Supplier<T> configurationValue,
            String parameterName) {
        T value = exchange.getIn().getHeader(headerName, headerType);
        if (ObjectHelper.isEmpty(value)) {
            value = configurationValue.get();
            LOG.trace("CloudWatch {} is missing, using default one [{}]", parameterName, value);
        }
        return value;
    }

    /**
     * Gets a required value from the exchange header, falling back to configuration if not present.
     */
    private <T> T getRequiredHeader(
            Exchange exchange,
            String headerName,
            Class<T> headerType,
            Supplier<T> configurationValue,
            String errorMessage) {
        T value = exchange.getIn().getHeader(headerName, headerType);
        if (ObjectHelper.isEmpty(value)) {
            value = configurationValue.get();
        }
        if (ObjectHelper.isEmpty(value)) {
            throw new IllegalArgumentException(errorMessage);
        }
        return value;
    }

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }

    protected Cw2Configuration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public String toString() {
        if (cwProducerToString == null) {
            cwProducerToString = "CwProducer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return cwProducerToString;
    }

    @Override
    public Cw2Endpoint getEndpoint() {
        return (Cw2Endpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        // health-check is optional so discover and resolve
        healthCheckRepository = HealthCheckHelper.getHealthCheckRepository(
                getEndpoint().getCamelContext(),
                "producers",
                WritableHealthCheckRepository.class);

        if (ObjectHelper.isNotEmpty(healthCheckRepository)) {
            String id = getEndpoint().getId();
            producerHealthCheck = new Cw2ProducerHealthCheck(getEndpoint(), id);
            producerHealthCheck.setEnabled(getEndpoint().getComponent().isHealthCheckProducerEnabled());
            healthCheckRepository.addHealthCheck(producerHealthCheck);
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (ObjectHelper.isNotEmpty(healthCheckRepository) && ObjectHelper.isNotEmpty(producerHealthCheck)) {
            healthCheckRepository.removeHealthCheck(producerHealthCheck);
            producerHealthCheck = null;
        }
    }

}
