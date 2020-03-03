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
package org.apache.camel.component.splunk;

import java.net.ConnectException;
import java.net.SocketException;
import java.util.regex.Pattern;

import javax.net.ssl.SSLException;

import com.splunk.Service;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The splunk component allows to publish or search for events in Splunk.
 */
@UriEndpoint(firstVersion = "2.13.0", scheme = "splunk", title = "Splunk", syntax = "splunk:name", label = "log,monitoring")
public class SplunkEndpoint extends ScheduledPollEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(SplunkEndpoint.class);

    private static final Pattern SPLUNK_SCHEMA_PATTERN = Pattern.compile("splunk:(//)*");
    private static final Pattern SPLUNK_OPTIONS_PATTER = Pattern.compile("\\?.*");

    private Service service;
    @UriParam
    private SplunkConfiguration configuration;

    public SplunkEndpoint() {
    }

    public SplunkEndpoint(String uri, SplunkComponent component, SplunkConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        String[] uriSplit = splitUri(getEndpointUri());
        if (uriSplit.length > 0) {
            ProducerType producerType = ProducerType.fromUri(uriSplit[0]);
            return new SplunkProducer(this, producerType);
        }
        throw new IllegalArgumentException("Cannot create any producer with uri " + getEndpointUri() + ". A producer type was not provided (or an incorrect pairing was used).");
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        if (configuration.getInitEarliestTime() == null) {
            throw new IllegalArgumentException("Required initialEarliestTime option could not be found");
        }
        String[] uriSplit = splitUri(getEndpointUri());
        if (uriSplit.length > 0) {
            ConsumerType consumerType = ConsumerType.fromUri(uriSplit[0]);
            SplunkConsumer consumer = new SplunkConsumer(this, processor, consumerType);
            configureConsumer(consumer);
            return consumer;
        }
        throw new IllegalArgumentException("Cannot create any consumer with uri " + getEndpointUri() + ". A consumer type was not provided (or an incorrect pairing was used).");
    }

    @Override
    protected void doStop() throws Exception {
        service = null;
        super.doStop();
    }

    public Service getService() {
        if (service == null) {
            this.service = configuration.getConnectionFactory().createService(getCamelContext());
        }
        return service;
    }

    private static String[] splitUri(String uri) {
        uri = SPLUNK_SCHEMA_PATTERN.matcher(uri).replaceAll("");
        uri = SPLUNK_OPTIONS_PATTER.matcher(uri).replaceAll("");

        return uri.split("/");
    }

    public SplunkConfiguration getConfiguration() {
        return configuration;
    }

    public synchronized boolean reset(Exception e) {
        boolean answer = false;
        if ((e instanceof RuntimeException && ((RuntimeException)e).getCause() instanceof ConnectException) || ((e instanceof SocketException) || (e instanceof SSLException))) {
            LOG.warn("Got exception from Splunk. Service will be reset.");
            this.service = null;
            answer = true;
        }
        return answer;
    }
}
