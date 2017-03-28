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
package org.apache.camel.component.metrics;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.metrics.MetricsConstants.HEADER_GAUGE_SUBJECT;

public class GaugeProducer extends AbstractMetricsProducer {

    private static final Logger LOG = LoggerFactory.getLogger(GaugeProducer.class);

    public GaugeProducer(MetricsEndpoint endpoint) {
        super(endpoint);
        Gauge<?> gauge = endpoint.getRegistry().getGauges().get(endpoint.getMetricsName());
        if (gauge instanceof CamelMetricsGauge) {
            CamelMetricsGauge camelMetricsGauge = (CamelMetricsGauge)gauge;
            if (endpoint.getSubject() != null) {
                camelMetricsGauge.setValue(endpoint.getSubject());
            }
        } else {
            if (endpoint.getSubject() != null) {
                endpoint.getRegistry().register(endpoint.getMetricsName(), new CamelMetricsGauge(endpoint.getSubject()));
            } else {
                LOG.info("No subject found for Gauge \"{}\". Ignoring...", endpoint.getMetricsName());
            }
        }
    }

    @Override
    protected void doProcess(Exchange exchange, MetricsEndpoint endpoint, MetricRegistry registry, String metricsName) throws Exception {
        Gauge<?> gauge = registry.getGauges().get(metricsName);
        if (gauge instanceof CamelMetricsGauge) {
            CamelMetricsGauge camelMetricsGauge = (CamelMetricsGauge)gauge;
            Object subject = exchange.getIn().getHeader(HEADER_GAUGE_SUBJECT, Object.class);
            if (subject != null) {
                camelMetricsGauge.setValue(subject);
            }
        } else {
            Object subject = exchange.getIn().getHeader(HEADER_GAUGE_SUBJECT, Object.class);
            Object finalSubject = subject != null ? subject : endpoint.getSubject();
            if (finalSubject != null) {
                registry.register(metricsName, new CamelMetricsGauge(finalSubject));
            } else {
                LOG.info("No subject found for Gauge \"{}\". Ignoring...", metricsName);
            }
        }
    }
    
    class CamelMetricsGauge implements Gauge<Object> {
        private Object subject;
        
        CamelMetricsGauge(Object subject) {
            this.subject = subject;
        }
        
        @Override
        public Object getValue() {
            return subject;
        }
        
        public void setValue(Object subject) {
            this.subject = subject;
        }
    }
}
