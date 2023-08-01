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

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.health.WritableHealthCheckRepository;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
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
        List<MetricDatum> metricData = getMetricData(exchange);

        PutMetricDataRequest.Builder builder = PutMetricDataRequest.builder();
        builder.metricData(metricData).namespace(determineNameSpace(exchange));

        PutMetricDataRequest request = builder.build();
        LOG.info("Sending request [{}] from exchange [{}]...", request, exchange);
        getEndpoint().getCloudWatchClient().putMetricData(request);
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
        if (name != null && value != null) {
            metricDatum.dimensions(Dimension.builder().name(name).value(value).build());
        } else {
            @SuppressWarnings("unchecked")
            Map<String, String> dimensions = exchange.getIn().getHeader(Cw2Constants.METRIC_DIMENSIONS, Map.class);
            if (dimensions != null) {
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
        Instant timestamp = exchange.getIn().getHeader(Cw2Constants.METRIC_TIMESTAMP, Instant.class);
        if (timestamp == null) {
            timestamp = getConfiguration().getTimestamp();
        }
        return timestamp;
    }

    private String determineNameSpace(Exchange exchange) {
        String namespace = exchange.getIn().getHeader(Cw2Constants.METRIC_NAMESPACE, String.class);
        if (namespace == null) {
            namespace = getConfiguration().getNamespace();
        }
        return namespace;
    }

    private String determineName(Exchange exchange) {
        String name = exchange.getIn().getHeader(Cw2Constants.METRIC_NAME, String.class);
        if (name == null) {
            name = getConfiguration().getName();
        }
        return name;
    }

    private Double determineValue(Exchange exchange) {
        Double value = exchange.getIn().getHeader(Cw2Constants.METRIC_VALUE, Double.class);
        if (value == null) {
            value = getConfiguration().getValue();
        }
        return value != null ? value : Double.valueOf(1);
    }

    private StandardUnit determineUnit(Exchange exchange) {
        String unit = exchange.getIn().getHeader(Cw2Constants.METRIC_UNIT, String.class);
        if (unit == null) {
            unit = getConfiguration().getUnit();
        }
        return unit != null ? StandardUnit.fromValue(unit) : StandardUnit.COUNT;
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

        if (healthCheckRepository != null) {
            String id = getEndpoint().getId();
            producerHealthCheck = new Cw2ProducerHealthCheck(getEndpoint(), id);
            producerHealthCheck.setEnabled(getEndpoint().getComponent().isHealthCheckProducerEnabled());
            healthCheckRepository.addHealthCheck(producerHealthCheck);
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (healthCheckRepository != null && producerHealthCheck != null) {
            healthCheckRepository.removeHealthCheck(producerHealthCheck);
            producerHealthCheck = null;
        }
    }

}
