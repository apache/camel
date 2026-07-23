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
package org.apache.camel.spi;

import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.NamedNode;
import org.apache.camel.Ordered;
import org.apache.camel.Processor;
import org.jspecify.annotations.Nullable;

/**
 * SPI that wraps EIP {@link org.apache.camel.Processor}s with instrumentation so that per-exchange statistics can be
 * collected and exposed via JMX MBeans.
 * <p/>
 * At route startup, the JMX management lifecycle strategy calls
 * {@link #createProcessor(org.apache.camel.NamedNode, org.apache.camel.Processor)} for each processor defined in a
 * route. The returned {@link InstrumentationProcessor} delegates to the real processor while invoking
 * {@link InstrumentationProcessor#before} and {@link InstrumentationProcessor#after} around each exchange to record
 * timing, exchange counts, and error counts. Those counters are then surfaced through the corresponding managed MBean
 * (for example, a processor MBean registered by {@link ManagementObjectStrategy}).
 * <p/>
 * The inner {@link InstrumentationProcessor} interface extends {@link org.apache.camel.AsyncProcessor} and
 * {@link org.apache.camel.Ordered} so that multiple interceptors can be stacked in a defined order without blocking the
 * async dispatch path.
 * <p/>
 * See <a href="https://camel.apache.org/manual/jmx.html">JMX</a> in the Camel user manual.
 *
 * @see ManagementStrategy
 * @see ManagementObjectStrategy
 */
public interface ManagementInterceptStrategy {

    @Nullable
    InstrumentationProcessor<?> createProcessor(NamedNode definition, Processor target);

    @Nullable
    InstrumentationProcessor<?> createProcessor(String type);

    interface InstrumentationProcessor<T> extends AsyncProcessor, Ordered {

        @Nullable
        T before(Exchange exchange);

        void after(Exchange exchange, @Nullable T data);

        void setProcessor(Processor processor);

        void setCounter(Object object);
    }
}
