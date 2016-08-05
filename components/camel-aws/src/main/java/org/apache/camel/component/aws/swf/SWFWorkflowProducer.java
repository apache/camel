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

import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class SWFWorkflowProducer extends DefaultProducer {
    private static final transient Logger LOGGER = LoggerFactory.getLogger(SWFWorkflowProducer.class);
    private final CamelSWFWorkflowClient camelSWFClient;
    private SWFEndpoint endpoint;
    private SWFConfiguration configuration;
    
    private transient String swfWorkflowProducerToString;

    public SWFWorkflowProducer(SWFEndpoint endpoint, CamelSWFWorkflowClient camelSWFClient) {
        super(endpoint);
        this.endpoint = endpoint;
        this.configuration = endpoint.getConfiguration();
        this.camelSWFClient = camelSWFClient;
    }

    public void process(Exchange exchange) throws Exception {
        LOGGER.debug("processing workflow task " + exchange);

        try {

            Operation operation = getOperation(exchange);
            switch (operation) {

            case CANCEL:
                camelSWFClient.requestCancelWorkflowExecution(getWorkflowId(exchange), getRunId(exchange));
                break;

            case GET_STATE:
                Object state = camelSWFClient.getWorkflowExecutionState(getWorkflowId(exchange), getRunId(exchange), getResultType(exchange));
                endpoint.setResult(exchange, state);
                break;

            case DESCRIBE:
                Map<String, Object> workflowInfo = camelSWFClient.describeWorkflowInstance(getWorkflowId(exchange), getRunId(exchange));
                endpoint.setResult(exchange, workflowInfo);
                break;

            case GET_HISTORY:
                Object history = camelSWFClient.getWorkflowExecutionHistory(getWorkflowId(exchange), getRunId(exchange));
                endpoint.setResult(exchange, history);
                break;

            case START:
                String[] ids = camelSWFClient.startWorkflowExecution(getWorkflowId(exchange), getRunId(exchange),
                        getEventName(exchange), getVersion(exchange), getArguments(exchange), getTags(exchange));
                setHeader(exchange, SWFConstants.WORKFLOW_ID, ids[0]);
                setHeader(exchange, SWFConstants.RUN_ID, ids[1]);
                break;

            case SIGNAL:
                camelSWFClient.signalWorkflowExecution(getWorkflowId(exchange), getRunId(exchange), getSignalName(exchange), getArguments(exchange));
                break;

            case TERMINATE:
                camelSWFClient.terminateWorkflowExecution(getWorkflowId(exchange), getRunId(exchange), getReason(exchange), getDetails(exchange), getChildPolicy(exchange));
                break;

            default:
                throw new UnsupportedOperationException(operation.toString());
            }

        } catch (Throwable throwable) {
            throw new Exception(throwable);
        }
    }

    private String getEventName(Exchange exchange) {
        String eventName = exchange.getIn().getHeader(SWFConstants.EVENT_NAME, String.class);
        return eventName != null ? eventName : configuration.getEventName();
    }

    private String getVersion(Exchange exchange) {
        String version = exchange.getIn().getHeader(SWFConstants.VERSION, String.class);
        return version != null ? version : configuration.getVersion();
    }
    
    private List<String> getTags(Exchange exchange) {
        return exchange.getIn().getHeader(SWFConstants.TAGS, List.class);
    }

    private String getSignalName(Exchange exchange) {
        String signalName = exchange.getIn().getHeader(SWFConstants.SIGNAL_NAME, String.class);
        return signalName != null ? signalName : configuration.getSignalName();
    }

    private String getChildPolicy(Exchange exchange) {
        String childPolicy = exchange.getIn().getHeader(SWFConstants.CHILD_POLICY, String.class);
        return childPolicy != null ? childPolicy : configuration.getChildPolicy();
    }

    private String getDetails(Exchange exchange) {
        String details = exchange.getIn().getHeader(SWFConstants.DETAILS, String.class);
        return details != null ? details : configuration.getTerminationDetails();
    }

    private String getReason(Exchange exchange) {
        String reason = exchange.getIn().getHeader(SWFConstants.REASON, String.class);
        return reason != null ? reason : configuration.getTerminationReason();
    }

    private String getWorkflowId(Exchange exchange) {
        return exchange.getIn().getHeader(SWFConstants.WORKFLOW_ID, String.class);
    }

    private String getRunId(Exchange exchange) {
        return exchange.getIn().getHeader(SWFConstants.RUN_ID, String.class);
    }

    private Class<?> getResultType(Exchange exchange) throws ClassNotFoundException {
        String type = exchange.getIn().getHeader(SWFConstants.STATE_RESULT_TYPE, String.class);
        if (type == null) {
            type = configuration.getStateResultType();
        }

        return type != null ? Class.forName(type) : Object.class;
    }

    private Operation getOperation(Exchange exchange) {
        String operation = exchange.getIn().getHeader(SWFConstants.OPERATION, String.class);
        if (operation == null) {
            operation = configuration.getOperation();
        }

        return operation != null ? Operation.valueOf(operation) : Operation.START;
    }

    private void setHeader(Exchange exchange, String key, Object value) {
        if (ExchangeHelper.isOutCapable(exchange)) {
            exchange.getOut().setHeader(key, value);
        } else {
            exchange.getIn().setHeader(key, value);
        }
    }

    private Object getArguments(Exchange exchange) {
        return exchange.getIn().getBody();
    }


    @Override
    public String toString() {
        if (swfWorkflowProducerToString == null) {
            swfWorkflowProducerToString = "SWFWorkflowProducer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return swfWorkflowProducerToString;
    }

    private enum Operation {
        SIGNAL, CANCEL, TERMINATE, GET_STATE, START, DESCRIBE, GET_HISTORY;
    }
}
