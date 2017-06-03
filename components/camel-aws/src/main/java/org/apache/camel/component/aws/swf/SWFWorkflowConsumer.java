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
package org.apache.camel.component.aws.swf;

import java.util.Arrays;

import com.amazonaws.services.simpleworkflow.flow.worker.GenericWorkflowWorker;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.apache.camel.ExchangePattern.InOnly;

public class SWFWorkflowConsumer extends DefaultConsumer {
    private static final transient Logger LOGGER = LoggerFactory.getLogger(SWFWorkflowProducer.class);
    private SWFEndpoint endpoint;
    private final SWFConfiguration configuration;
    private GenericWorkflowWorker genericWorker;
    
    private transient String swfWorkflowConsumerToString;

    public SWFWorkflowConsumer(SWFEndpoint endpoint, Processor processor, SWFConfiguration configuration) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.configuration = configuration;
    }

    public Object processWorkflow(Object[] parameters, long startTime, boolean replaying) throws Exception {
        LOGGER.debug("Processing workflow task: " + Arrays.toString(parameters));
        Exchange exchange = endpoint.createExchange(parameters, SWFConstants.EXECUTE_ACTION);
        exchange.getIn().setHeader(SWFConstants.WORKFLOW_START_TIME, startTime);
        exchange.getIn().setHeader(SWFConstants.WORKFLOW_REPLAYING, replaying);

        getProcessor().process(exchange);
        return endpoint.getResult(exchange);
    }

    public void signalRecieved(Object[] parameters) throws Exception {
        LOGGER.debug("signalRecieved: " + Arrays.toString(parameters));

        Exchange exchange = endpoint.createExchange(parameters, SWFConstants.SIGNAL_RECEIVED_ACTION);
        exchange.setPattern(InOnly);
        getProcessor().process(exchange);
    }

    public Object getWorkflowState(Object parameters) throws Exception {
        LOGGER.debug("getWorkflowState: " + parameters);

        Exchange exchange = endpoint.createExchange(parameters, SWFConstants.GET_STATE_ACTION);
        getProcessor().process(exchange);
        return endpoint.getResult(exchange);
    }

    @Override
    protected void doStart() throws Exception {
        CamelWorkflowDefinitionFactoryFactory factoryFactory = new CamelWorkflowDefinitionFactoryFactory(this, configuration);
        genericWorker = new GenericWorkflowWorker(endpoint.getSWClient(), configuration.getDomainName(), configuration.getWorkflowList());
        genericWorker.setWorkflowDefinitionFactoryFactory(factoryFactory);
        genericWorker.start();
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        genericWorker.setDisableServiceShutdownOnStop(true);
        genericWorker.shutdownNow();
        super.doStop();
    }

    @Override
    public String toString() {
        if (swfWorkflowConsumerToString == null) {
            swfWorkflowConsumerToString = "SWFWorkflowConsumer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return swfWorkflowConsumerToString;
    }
}
