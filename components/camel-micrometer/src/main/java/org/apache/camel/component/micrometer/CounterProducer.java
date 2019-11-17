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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.apache.camel.Exchange;
import org.apache.camel.Message;

import static org.apache.camel.component.micrometer.MicrometerConstants.HEADER_COUNTER_DECREMENT;
import static org.apache.camel.component.micrometer.MicrometerConstants.HEADER_COUNTER_INCREMENT;

public class CounterProducer extends AbstractMicrometerProducer<Counter> {

    public CounterProducer(MicrometerEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    protected Function<MeterRegistry, Counter> registrar(String name, Iterable<Tag> tags) {
        return meterRegistry -> meterRegistry.counter(name, tags);
    }

    @Override
    protected void doProcess(Exchange exchange, MicrometerEndpoint endpoint, Counter counter) {
        Message in = exchange.getIn();
        Double increment = simple(exchange, endpoint.getIncrement(), Double.class);
        Double decrement = simple(exchange, endpoint.getDecrement(), Double.class);
        Double finalIncrement = getDoubleHeader(in, HEADER_COUNTER_INCREMENT, increment);
        Double finalDecrement = getDoubleHeader(in, HEADER_COUNTER_DECREMENT, decrement);
        if (finalIncrement != null) {
            counter.increment(finalIncrement);
        } else if (finalDecrement != null) {
            counter.increment(-finalDecrement);
        } else {
            counter.increment();
        }
    }
}
