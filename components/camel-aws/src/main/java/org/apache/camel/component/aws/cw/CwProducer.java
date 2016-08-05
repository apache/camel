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


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.MetricDatum;
import com.amazonaws.services.cloudwatch.model.PutMetricDataRequest;
import com.amazonaws.services.cloudwatch.model.StandardUnit;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.URISupport;

/**
 * A Producer which sends messages to the AWS CloudWatch Service
 */
public class CwProducer extends DefaultProducer {

    private transient String cwProducerToString;
    
    public CwProducer(Endpoint endpoint) {
        super(endpoint);
    }

    public void process(Exchange exchange) throws Exception {
        List<MetricDatum> metricData = getMetricData(exchange);

        PutMetricDataRequest request = new PutMetricDataRequest()
                .withMetricData(metricData)
                .withNamespace(determineNameSpace(exchange));

        log.info("Sending request [{}] from exchange [{}]...", request, exchange);
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

        MetricDatum metricDatum = new MetricDatum()
                .withMetricName(determineName(exchange))
                .withValue(determineValue(exchange))
                .withUnit(determineUnit(exchange))
                .withTimestamp(determineTimestamp(exchange));
        setDimension(metricDatum, exchange);
        return Arrays.asList(metricDatum);
    }

    private void setDimension(MetricDatum metricDatum, Exchange exchange) {
        String name = exchange.getIn().getHeader(CwConstants.METRIC_DIMENSION_NAME, String.class);
        String value = exchange.getIn().getHeader(CwConstants.METRIC_DIMENSION_VALUE, String.class);
        if (name != null && value != null) {
            metricDatum.withDimensions(new Dimension().withName(name).withValue(value));
        } else {
            Map<String, String> dimensions = exchange.getIn().getHeader(CwConstants.METRIC_DIMENSIONS, Map.class);
            if (dimensions != null) {
                Collection<Dimension> dimensionCollection = new ArrayList<Dimension>();
                for (Map.Entry<String, String> dimensionEntry : dimensions.entrySet()) {
                    Dimension dimension = new Dimension().withName(dimensionEntry.getKey()).withValue(dimensionEntry.getValue());
                    dimensionCollection.add(dimension);
                }
                metricDatum.withDimensions(dimensionCollection);
            }
        }
    }

    private Date determineTimestamp(Exchange exchange) {
        Date timestamp = exchange.getIn().getHeader(CwConstants.METRIC_TIMESTAMP, Date.class);
        if (timestamp == null) {
            timestamp = getConfiguration().getTimestamp();
        }
        return timestamp;
    }

    private String determineNameSpace(Exchange exchange) {
        String namespace = exchange.getIn().getHeader(CwConstants.METRIC_NAMESPACE, String.class);
        if (namespace == null) {
            namespace = getConfiguration().getNamespace();
        }
        return namespace;
    }

    private String determineName(Exchange exchange) {
        String name = exchange.getIn().getHeader(CwConstants.METRIC_NAME, String.class);
        if (name == null) {
            name = getConfiguration().getName();
        }
        return name;
    }

    private Double determineValue(Exchange exchange) {
        Double value = exchange.getIn().getHeader(CwConstants.METRIC_VALUE, Double.class);
        if (value == null) {
            value = getConfiguration().getValue();
        }
        return value != null ? value : Double.valueOf(1);
    }

    private StandardUnit determineUnit(Exchange exchange) {
        String unit = exchange.getIn().getHeader(CwConstants.METRIC_UNIT, String.class);
        if (unit == null) {
            unit = getConfiguration().getUnit();
        }
        return unit != null ? StandardUnit.valueOf(unit) : StandardUnit.Count;
    }

    protected CwConfiguration getConfiguration() {
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
    public CwEndpoint getEndpoint() {
        return (CwEndpoint) super.getEndpoint();
    }
}