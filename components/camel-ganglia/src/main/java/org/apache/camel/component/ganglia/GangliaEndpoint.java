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
package org.apache.camel.component.ganglia;

import info.ganglia.gmetric4j.Publisher;
import info.ganglia.gmetric4j.gmetric.GMetric;
import info.ganglia.gmetric4j.gmetric.GMetricPublisher;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * The ganglia component is used for sending metrics to the Ganglia monitoring system.
 */
@UriEndpoint(firstVersion = "2.15.0", scheme = "ganglia", title = "Ganglia", syntax = "ganglia:host:port", producerOnly = true, label = "monitoring")
public class GangliaEndpoint extends DefaultEndpoint {

    private Publisher publisher;

    @UriParam
    private GangliaConfiguration configuration;

    public GangliaEndpoint() {
    }

    public GangliaEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    @Override
    public Producer createProducer() throws Exception {
        ObjectHelper.notNull(configuration, "configuration");
        return new GangliaProducer(this, getPublisher());
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Ganglia consumer not supported");
    }

    public GangliaConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(GangliaConfiguration configuration) {
        this.configuration = configuration;
    }

    public synchronized Publisher getPublisher() {
        if (publisher == null) {
            GMetric gmetric = configuration.createGMetric();
            publisher = new GMetricPublisher(gmetric);
        }
        return publisher;
    }

    public void setPublisher(Publisher publisher) {
        this.publisher = publisher;
    }
}
