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

import java.io.Closeable;
import java.io.IOException;

import com.github.dockerjava.api.async.ResultCallbackTemplate;
import com.github.dockerjava.api.command.StatsCmd;
import com.github.dockerjava.api.model.Statistics;
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
    /**
     * The callback of the statistics stream. docker-java opens the stream on its own thread and the stream belongs to
     * this callback: closing the command does not close it.
     */
    private volatile StatsCallback callback;

    public DockerStatsConsumer(DockerEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.component = (DockerComponent) endpoint.getComponent();
    }

    @Override
    public DockerEndpoint getEndpoint() {
        return (DockerEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        String containerId = DockerHelper.getProperty(DockerConstants.DOCKER_CONTAINER_ID, endpoint.getConfiguration(), null,
                String.class);

        this.statsCmd = DockerClientFactory.getDockerClient(component, endpoint.getConfiguration(), null).statsCmd(containerId);
        StatsCallback newCallback = new StatsCallback();
        this.callback = newCallback;
        this.statsCmd.exec(newCallback);

        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        StatsCallback current = callback;
        callback = null;
        if (current != null) {
            current.close();
        }
        this.statsCmd.close();

        super.doStop();
    }

    protected class StatsCallback extends ResultCallbackTemplate<StatsCallback, Statistics> {

        private volatile boolean closed;

        @Override
        public void onStart(Closeable stream) {
            super.onStart(stream);
            if (closed) {
                // closed (the consumer was stopped) while the stream was being opened: close the stream now
                try {
                    close();
                } catch (IOException e) {
                    LOGGER.debug("Error closing the Docker statistics stream", e);
                }
            }
        }

        @Override
        public void onNext(Statistics statistics) {
            if (closed) {
                // statistics of a stream the consumer no longer reads
                return;
            }
            LOGGER.debug("Received Docker Statistics Event: {}", statistics);

            final Exchange exchange = createExchange(true);
            Message message = exchange.getIn();
            message.setBody(statistics);

            // use default consumer callback
            AsyncCallback cb = defaultConsumerCallback(exchange, true);
            getAsyncProcessor().process(exchange, cb);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
