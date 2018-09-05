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
package org.apache.camel.component.docker;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.docker.consumer.DockerEventsConsumer;
import org.apache.camel.component.docker.consumer.DockerStatsConsumer;
import org.apache.camel.component.docker.exception.DockerException;
import org.apache.camel.component.docker.producer.AsyncDockerProducer;
import org.apache.camel.component.docker.producer.DockerProducer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;

/**
 * The docker component is used for managing Docker containers.
 */
@UriEndpoint(firstVersion = "2.15.0", scheme = "docker", title = "Docker", syntax = "docker:operation",
        label = "container,cloud,paas", lenientProperties = true)
public class DockerEndpoint extends DefaultEndpoint {

    @UriParam
    private DockerConfiguration configuration;

    public DockerEndpoint() {
    }

    public DockerEndpoint(String uri, DockerComponent component, DockerConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    public DockerEndpoint(String endpointUri) {
        super(endpointUri);
    }

    @Override
    public Producer createProducer() throws Exception {
        DockerOperation operation = configuration.getOperation();

        if (operation != null && operation.canProduce()) {
            if (operation.isAsync()) {
                return new AsyncDockerProducer(this);
            } else {
                return new DockerProducer(this);
            }
        } else {
            throw new DockerException(operation + " is not a valid producer operation");
        }
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        DockerOperation operation = configuration.getOperation();

        switch (operation) {
        case EVENTS:
            return new DockerEventsConsumer(this, processor);
        case STATS:
            return new DockerStatsConsumer(this, processor);
        default:
            throw new DockerException(operation + " is not a valid consumer operation");
        }
    }
    
    @Override
    public boolean isSingleton() {
        return true;
    }

    public DockerConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public boolean isLenientProperties() {
        return true;
    }



}
