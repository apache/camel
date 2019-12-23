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
package org.apache.camel.component.docker.consumer;

import com.github.dockerjava.api.command.StatsCmd;
import com.github.dockerjava.api.model.Statistics;
import com.github.dockerjava.core.async.ResultCallbackTemplate;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.docker.DockerClientFactory;
import org.apache.camel.component.docker.DockerComponent;
import org.apache.camel.component.docker.DockerConstants;
import org.apache.camel.component.docker.DockerEndpoint;
import org.apache.camel.component.docker.DockerHelper;
import org.apache.camel.support.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Docker Consumer for streaming statistical events
 */
public class DockerStatsConsumer extends DefaultConsumer {

    private static final transient Logger LOGGER = LoggerFactory.getLogger(DockerStatsConsumer.class);

    private DockerEndpoint endpoint;
    private DockerComponent component;
    private StatsCmd statsCmd;

    public DockerStatsConsumer(DockerEndpoint endpoint, Processor processor) throws Exception {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.component = (DockerComponent)endpoint.getComponent();
    }

    @Override
    public DockerEndpoint getEndpoint() {
        return (DockerEndpoint)super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        String containerId = DockerHelper.getProperty(DockerConstants.DOCKER_CONTAINER_ID, endpoint.getConfiguration(), null, String.class);

        this.statsCmd = DockerClientFactory.getDockerClient(component, endpoint.getConfiguration(), null).statsCmd(containerId);
        this.statsCmd.exec(new StatsCallback());

        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        this.statsCmd.close();

        super.doStop();
    }

    protected class StatsCallback extends ResultCallbackTemplate<StatsCallback, Statistics> {

        @Override
        public void onNext(Statistics statistics) {
            LOGGER.debug("Received Docker Statistics Event: " + statistics);

            final Exchange exchange = getEndpoint().createExchange();
            Message message = exchange.getIn();
            message.setBody(statistics);

            try {
                LOGGER.trace("Processing exchange [{}]...", exchange);
                getAsyncProcessor().process(exchange, new AsyncCallback() {
                    @Override
                    public void done(boolean doneSync) {
                        LOGGER.trace("Done processing exchange [{}]...", exchange);
                    }
                });
            } catch (Exception e) {
                exchange.setException(e);
            }
            if (exchange.getException() != null) {
                getExceptionHandler().handleException("Error processing exchange", exchange, exchange.getException());
            }
        }
    }
}
