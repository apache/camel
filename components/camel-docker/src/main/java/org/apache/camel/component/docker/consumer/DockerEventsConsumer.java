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
package org.apache.camel.component.docker.consumer;

import java.util.Date;
import java.util.concurrent.ExecutorService;

import com.github.dockerjava.api.command.EventCallback;
import com.github.dockerjava.api.command.EventsCmd;
import com.github.dockerjava.api.model.Event;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.docker.DockerClientFactory;
import org.apache.camel.component.docker.DockerComponent;
import org.apache.camel.component.docker.DockerConstants;
import org.apache.camel.component.docker.DockerEndpoint;
import org.apache.camel.component.docker.DockerHelper;
import org.apache.camel.impl.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Docker Consumer for streaming events
 */
public class DockerEventsConsumer extends DefaultConsumer implements EventCallback {

    private static final transient Logger LOGGER = LoggerFactory.getLogger(DockerEventsConsumer.class);

    private DockerEndpoint endpoint;

    private DockerComponent component;

    private EventsCmd eventsCmd;

    private ExecutorService eventsExecutorService;

    public DockerEventsConsumer(DockerEndpoint endpoint, Processor processor) throws Exception {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.component = (DockerComponent) endpoint.getComponent();

    }

    @Override
    public DockerEndpoint getEndpoint() {
        return (DockerEndpoint) super.getEndpoint();
    }


    /**
     * Determine the point in time to begin streaming events
     */
    private long processInitialEvent() {

        long currentTime = new Date().getTime();

        Long initialRange = DockerHelper.getProperty(DockerConstants.DOCKER_INITIAL_RANGE, endpoint.getConfiguration(), null, Long.class);

        if (initialRange != null) {
            currentTime = currentTime - initialRange;
        }

        return currentTime;


    }

    @Override
    protected void doStart() throws Exception {

        eventsCmd = DockerClientFactory.getDockerClient(component, endpoint.getConfiguration(), null).eventsCmd(this);

        eventsCmd.withSince(String.valueOf(processInitialEvent()));
        eventsExecutorService = eventsCmd.exec();

        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {

        if (eventsExecutorService != null && !eventsExecutorService.isTerminated()) {
            LOGGER.trace("Stopping Docker events Executor Service");

            eventsExecutorService.shutdown();
        }

        super.doStop();
    }


    @Override
    public void onEvent(Event event) {

        LOGGER.debug("Received Docker Event: " + event);

        final Exchange exchange = getEndpoint().createExchange();
        Message message = exchange.getIn();
        message.setBody(event);

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

    @Override
    public void onException(Throwable throwable) {
        LOGGER.error("Error Consuming from Docker Events: {}", throwable.getMessage());
    }

    @Override
    public void onCompletion(int numEvents) {

        LOGGER.debug("Docker events connection completed. Events processed : {}", numEvents);

        eventsCmd.withSince(null);

        LOGGER.debug("Reestablishing connection with Docker");
        eventsCmd.exec();

    }

    @Override
    public boolean isReceiving() {
        return isRunAllowed();
    }
}
