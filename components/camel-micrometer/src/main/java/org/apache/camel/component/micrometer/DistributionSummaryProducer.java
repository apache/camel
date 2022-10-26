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
package org.apache.camel.component.micrometer;

import java.util.function.Function;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.apache.camel.Exchange;

import static org.apache.camel.component.micrometer.MicrometerConstants.HEADER_HISTOGRAM_VALUE;

public class DistributionSummaryProducer extends AbstractMicrometerProducer<DistributionSummary> {

    public DistributionSummaryProducer(MicrometerEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    protected Function<MeterRegistry, DistributionSummary> registrar(String name, String description, Iterable<Tag> tags) {
        return meterRegistry -> DistributionSummary.builder(name).description(description).tags(tags).register(meterRegistry);
    }

    @Override
    protected void doProcess(Exchange exchange, MicrometerEndpoint endpoint, DistributionSummary summary) {
        Double value = simple(exchange, endpoint.getValue(), Double.class);
        Double finalValue = getDoubleHeader(exchange.getIn(), HEADER_HISTOGRAM_VALUE, value);
        if (finalValue != null) {
            summary.record(finalValue);
        }
    }
}
