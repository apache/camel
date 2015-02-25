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
package org.apache.camel.component.ganglia;

import info.ganglia.gmetric4j.Publisher;
import info.ganglia.gmetric4j.gmetric.GMetricSlope;
import info.ganglia.gmetric4j.gmetric.GMetricType;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultProducer;

public class GangliaProducer extends DefaultProducer {

    private final Publisher publisher;
    private final GangliaEndpoint gangliaEndpoint;

    public GangliaProducer(GangliaEndpoint endpoint, Publisher publisher) {
        super(endpoint);
        this.gangliaEndpoint = endpoint;
        this.publisher = publisher;
    }

    public void process(Exchange exchange) throws Exception {
        Message message = exchange.getIn();

        GangliaConfiguration conf = gangliaEndpoint.getConfiguration();

        String groupName = conf.getGroupName();
        if (message.getHeaders().containsKey(GangliaConstants.GROUP_NAME)) {
            groupName = message.getHeader(GangliaConstants.GROUP_NAME, String.class);
        }

        String prefix = conf.getPrefix();
        String metricName = conf.getMetricName();
        if (message.getHeaders().containsKey(GangliaConstants.METRIC_NAME)) {
            metricName = message.getHeader(GangliaConstants.METRIC_NAME, String.class);
        }
        if (prefix != null && prefix.length() > 0) {
            metricName = prefix + "_" + metricName;
        }

        GMetricType type = conf.getType();
        if (message.getHeaders().containsKey(GangliaConstants.METRIC_TYPE)) {
            type = message.getHeader(GangliaConstants.METRIC_TYPE, GMetricType.class);
        }

        GMetricSlope slope = conf.getSlope();
        if (message.getHeaders().containsKey(GangliaConstants.METRIC_SLOPE)) {
            slope = message.getHeader(GangliaConstants.METRIC_SLOPE, GMetricSlope.class);
        }

        String units = conf.getUnits();
        if (message.getHeaders().containsKey(GangliaConstants.METRIC_UNITS)) {
            units = message.getHeader(GangliaConstants.METRIC_UNITS, String.class);
        }

        int tmax = conf.getTmax();
        if (message.getHeaders().containsKey(GangliaConstants.METRIC_TMAX)) {
            tmax = message.getHeader(GangliaConstants.METRIC_TMAX, Integer.class);
        }

        int dmax = conf.getDmax();
        if (message.getHeaders().containsKey(GangliaConstants.METRIC_DMAX)) {
            dmax = message.getHeader(GangliaConstants.METRIC_DMAX, Integer.class);
        }

        String value = message.getBody(String.class);
        if ((value == null || value.length() == 0)
            && (type == GMetricType.FLOAT || type == GMetricType.DOUBLE)) {
            log.debug("Metric {} string value was null, using NaN", metricName);
            value = "NaN";
        }

        if (log.isDebugEnabled()) {
            log.debug("Sending metric {} to Ganglia: {}", metricName, value);
        }
        publisher.publish(groupName,
            metricName, value, type, slope, tmax, dmax, units);
        log.trace("Sending metric done");
    }
}
