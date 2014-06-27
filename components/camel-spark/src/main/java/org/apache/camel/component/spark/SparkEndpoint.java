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
package org.apache.camel.component.spark;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;

@UriEndpoint(scheme = "spark", consumerClass =  SparkConsumer.class)
public class SparkEndpoint extends DefaultEndpoint {

    @UriParam
    SparkConfiguration sparkConfiguration;
    @UriParam
    private SparkBinding sparkBinding;
    @UriParam
    private String verb;
    @UriParam
    private String path;
    @UriParam
    private String acceptType;

    public SparkEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    public SparkConfiguration getSparkConfiguration() {
        return sparkConfiguration;
    }

    public void setSparkConfiguration(SparkConfiguration sparkConfiguration) {
        this.sparkConfiguration = sparkConfiguration;
    }

    public SparkBinding getSparkBinding() {
        return sparkBinding;
    }

    public void setSparkBinding(SparkBinding sparkBinding) {
        this.sparkBinding = sparkBinding;
    }

    public String getVerb() {
        return verb;
    }

    public void setVerb(String verb) {
        this.verb = verb;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getAcceptType() {
        return acceptType;
    }

    public void setAcceptType(String acceptType) {
        this.acceptType = acceptType;
    }

    @Override
    public Producer createProducer() throws Exception {
        throw new UnsupportedOperationException("Producer not supported");
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        CamelSparkRoute route = new CamelSparkRoute(this, processor);

        Consumer consumer = new SparkConsumer(this, processor, route);
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
}
