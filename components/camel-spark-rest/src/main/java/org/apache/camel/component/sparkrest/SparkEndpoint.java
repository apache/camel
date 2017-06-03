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
package org.apache.camel.component.sparkrest;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.ObjectHelper;
import spark.route.HttpMethod;

/**
 * The spark-rest component is used for hosting REST services which has been defined using Camel rest-dsl.
 */
@UriEndpoint(firstVersion = "2.14.0", scheme = "spark-rest", title = "Spark Rest", syntax = "spark-rest:verb:path", consumerOnly = true, consumerClass =  SparkConsumer.class, label = "rest")
public class SparkEndpoint extends DefaultEndpoint {
    @UriPath(enums = "get,post,put,patch,delete,head,trace,connect,options") @Metadata(required = "true")
    private String verb;
    @UriPath @Metadata(required = "true")
    private String path;
    @UriParam
    private String accept;
    @UriParam
    private SparkConfiguration sparkConfiguration;
    @UriParam(label = "advanced")
    private SparkBinding sparkBinding;

    public SparkEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    public SparkConfiguration getSparkConfiguration() {
        return sparkConfiguration;
    }

    /**
     * To use the SparkConfiguration
     */
    public void setSparkConfiguration(SparkConfiguration sparkConfiguration) {
        this.sparkConfiguration = sparkConfiguration;
    }

    public SparkBinding getSparkBinding() {
        return sparkBinding;
    }

    /**
     * To use a custom SparkBinding to map to/from Camel message.
     */
    public void setSparkBinding(SparkBinding sparkBinding) {
        this.sparkBinding = sparkBinding;
    }

    public String getVerb() {
        return verb;
    }

    /**
     * get, post, put, patch, delete, head, trace, connect, or options.
     */
    public void setVerb(String verb) {
        this.verb = verb;
    }

    public String getPath() {
        return path;
    }

    /**
     * The content path which support Spark syntax.
     */
    public void setPath(String path) {
        this.path = path;
    }

    public String getAccept() {
        return accept;
    }

    /**
     * Accept type such as: 'text/xml', or 'application/json'. By default we accept all kinds of types.
     */
    public void setAccept(String accept) {
        this.accept = accept;
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

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        ObjectHelper.notEmpty(verb, "verb", this);
        ObjectHelper.notEmpty(path, "path", this);

        // verb must be supported by Spark and lets convert to the actual name
        HttpMethod method = getCamelContext().getTypeConverter().mandatoryConvertTo(HttpMethod.class, verb);
        verb = method.name();
    }
}
