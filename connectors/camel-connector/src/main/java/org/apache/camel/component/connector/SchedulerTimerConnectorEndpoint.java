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
package org.apache.camel.component.connector;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.processor.Pipeline;

/**
 * A connector which is scheduler based from a timer endpoint. This allows to use a connector from a Camel route that
 * is scheduled. For example a foo connector would otherwise have to be manually scheduled, eg
 * <pre>
 *     from("timer:something?period=2000")
 *       .to("foo:hello")
 *       .log("Foo says ${body}");
 * </pre>
 * .. can now be done without the manual timer
 * <pre>
 *     from("foo:hello?schedulePeriod=2000")
 *       .log("Foo says ${body}");
 * </pre>
 * <p/>
 * This requires the connector to have configured: <tt>"scheduler": "timer"</tt> such as shown in the petstore-connector.
 */
@ManagedResource(description = "Managed Scheduled TimerConnector Endpoint")
public class SchedulerTimerConnectorEndpoint extends DefaultConnectorEndpoint {

    private long period = 1000;

    public SchedulerTimerConnectorEndpoint(String endpointUri, ConnectorComponent component, Endpoint endpoint, DataType inputDataType, DataType outputDataType) {
        super(endpointUri, component, endpoint, inputDataType, outputDataType);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        // special as we are scheduler based and then need to create a timer consumer that then calls the producer
        List<Processor> children = new ArrayList<>(2);
        children.add(createProducer());
        children.add(processor);
        Processor pipeline = Pipeline.newInstance(getCamelContext(), children);

        // create a timer consumer which wraps calling the producer and then the intended processor
        String name = getComponent().getComponentScheme();
        String uri = "timer:" + name + "?period=" + period;

        Consumer consumer = getCamelContext().getEndpoint(uri).createConsumer(pipeline);
        configureConsumer(consumer);
        return consumer;
    }

    @ManagedAttribute(description = "Delay in milli seconds between scheduling (executing)")
    public long getPeriod() {
        return period;
    }

    public void setPeriod(long period) {
        this.period = period;
    }
}
