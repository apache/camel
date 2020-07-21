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

import com.splunk.Args;
import org.apache.camel.Exchange;
import org.apache.camel.component.splunk.event.SplunkEvent;
import org.apache.camel.component.splunk.support.DataWriter;
import org.apache.camel.component.splunk.support.StreamDataWriter;
import org.apache.camel.component.splunk.support.SubmitDataWriter;
import org.apache.camel.component.splunk.support.TcpDataWriter;
import org.apache.camel.support.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Splunk producer.
 */
public class SplunkProducer extends DefaultProducer {
    private static final transient Logger LOG = LoggerFactory.getLogger(SplunkProducer.class);
    private SplunkEndpoint endpoint;
    private DataWriter dataWriter;

    public SplunkProducer(SplunkEndpoint endpoint, ProducerType producerType) {
        super(endpoint);
        this.endpoint = endpoint;
        createWriter(producerType);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        try {
            if (!dataWriter.isConnected()) {
                dataWriter.start();
            }
            if (endpoint.getConfiguration().isRaw()) {
                dataWriter.write(exchange.getIn().getMandatoryBody(String.class));
            } else {
                dataWriter.write(exchange.getIn().getMandatoryBody(SplunkEvent.class));
            }
        } catch (Exception e) {
            if (endpoint.reset(e)) {
                dataWriter.stop();
            }
            throw e;
        }
    }

    @Override
    protected void doStop() throws Exception {
        dataWriter.stop();
        super.doStop();
    }

    private void createWriter(ProducerType producerType) {
        switch (producerType) {
            case TCP: {
                LOG.debug("Creating TcpDataWriter");
                dataWriter = new TcpDataWriter(endpoint, buildSplunkArgs());
                ((TcpDataWriter)dataWriter).setPort(endpoint.getConfiguration().getTcpReceiverPort());
                LOG.debug("TcpDataWriter created for endpoint {}", endpoint);
                break;
            }
            case SUBMIT: {
                LOG.debug("Creating SubmitDataWriter");
                dataWriter = new SubmitDataWriter(endpoint, buildSplunkArgs());
                ((SubmitDataWriter)dataWriter).setIndex(endpoint.getConfiguration().getIndex());
                LOG.debug("SubmitDataWriter created for endpoint {}", endpoint);
                break;
            }
            case STREAM: {
                LOG.debug("Creating StreamDataWriter");
                dataWriter = new StreamDataWriter(endpoint, buildSplunkArgs());
                ((StreamDataWriter)dataWriter).setIndex(endpoint.getConfiguration().getIndex());
                LOG.debug("StreamDataWriter created for endpoint {}", endpoint);
                break;
            }
            default: {
                throw new RuntimeException("unknown producerType");
            }
        }
    }

    private Args buildSplunkArgs() {
        Args args = new Args();
        if (endpoint.getConfiguration().getSourceType() != null) {
            args.put("sourcetype", endpoint.getConfiguration().getSourceType());
        }
        if (endpoint.getConfiguration().getSource() != null) {
            args.put("source", endpoint.getConfiguration().getSource());
        }
        if (endpoint.getConfiguration().getEventHost() != null) {
            args.put("host", endpoint.getConfiguration().getEventHost());
        }
        return args;
    }

    protected DataWriter getDataWriter() {
        return dataWriter;
    }
}
