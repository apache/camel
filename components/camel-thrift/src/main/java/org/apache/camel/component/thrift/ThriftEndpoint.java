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
package org.apache.camel.component.thrift;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.SynchronousDelegateProducer;

/**
 * Call and expose remote procedures (RPC) with Apache Thrift data format and serialization mechanism.
 */
@UriEndpoint(firstVersion = "2.20.0", scheme = "thrift", title = "Thrift", syntax = "thrift:host:port/service",
             category = { Category.RPC, Category.TRANSFORMATION })
public class ThriftEndpoint extends DefaultEndpoint {
    @UriParam
    private ThriftConfiguration configuration;

    private String serviceName;
    private String servicePackage;

    public ThriftEndpoint(String uri, ThriftComponent component, ThriftConfiguration config) throws Exception {
        super(uri, component);
        this.configuration = config;

        // Extract service and package names from the full service name
        serviceName = ThriftUtils.extractServiceName(configuration.getService());
        servicePackage = ThriftUtils.extractServicePackage(configuration.getService());
    }

    public ThriftConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        ThriftProducer producer = new ThriftProducer(this, configuration);
        if (isSynchronous()) {
            return new SynchronousDelegateProducer(producer);
        } else {
            return producer;
        }
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        ThriftConsumer consumer = new ThriftConsumer(this, processor, configuration);
        configureConsumer(consumer);
        return consumer;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getServicePackage() {
        return servicePackage;
    }
}
