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
package org.apache.camel.component.salesforce;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.internal.OperationName;
import org.apache.camel.component.salesforce.internal.PayloadFormat;
import org.apache.camel.component.salesforce.internal.processor.AnalyticsApiProcessor;
import org.apache.camel.component.salesforce.internal.processor.BulkApiProcessor;
import org.apache.camel.component.salesforce.internal.processor.CompositeApiProcessor;
import org.apache.camel.component.salesforce.internal.processor.JsonRestProcessor;
import org.apache.camel.component.salesforce.internal.processor.SalesforceProcessor;
import org.apache.camel.component.salesforce.internal.processor.XmlRestProcessor;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.service.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Salesforce producer.
 */
public class SalesforceProducer extends DefaultAsyncProducer {

    private static final Logger LOG = LoggerFactory.getLogger(SalesforceProducer.class);

    private final SalesforceProcessor processor;

    public SalesforceProducer(SalesforceEndpoint endpoint) throws SalesforceException {
        super(endpoint);

        final SalesforceEndpointConfig endpointConfig = endpoint.getConfiguration();
        final PayloadFormat payloadFormat = endpointConfig.getFormat();

        // check if its a Bulk Operation
        final OperationName operationName = endpoint.getOperationName();
        if (isBulkOperation(operationName)) {
            processor = new BulkApiProcessor(endpoint);
        } else if (isAnalyticsOperation(operationName)) {
            processor = new AnalyticsApiProcessor(endpoint);
        } else if (isCompositeOperation(operationName)) {
            processor = new CompositeApiProcessor(endpoint);
        } else {
            // create an appropriate processor
            if (payloadFormat == PayloadFormat.JSON) {
                // create a JSON exchange processor
                processor = new JsonRestProcessor(endpoint);
            } else {
                processor = new XmlRestProcessor(endpoint);
            }
        }
    }

    private static boolean isBulkOperation(OperationName operationName) {
        switch (operationName) {
            case CREATE_JOB:
            case GET_JOB:
            case CLOSE_JOB:
            case ABORT_JOB:
            case CREATE_BATCH:
            case GET_BATCH:
            case GET_ALL_BATCHES:
            case GET_REQUEST:
            case GET_RESULTS:
            case CREATE_BATCH_QUERY:
            case GET_QUERY_RESULT_IDS:
            case GET_QUERY_RESULT:
                return true;
            default:
                return false;
        }
    }

    private static boolean isAnalyticsOperation(OperationName operationName) {
        switch (operationName) {
            case GET_RECENT_REPORTS:
            case GET_REPORT_DESCRIPTION:
            case EXECUTE_SYNCREPORT:
            case EXECUTE_ASYNCREPORT:
            case GET_REPORT_INSTANCES:
            case GET_REPORT_RESULTS:
                return true;
            default:
                return false;
        }
    }

    private static boolean isCompositeOperation(OperationName operationName) {
        switch (operationName) {
            case COMPOSITE_TREE:
            case COMPOSITE_BATCH:
            case COMPOSITE:
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        LOG.debug("Processing {}", ((SalesforceEndpoint)getEndpoint()).getOperationName());
        return processor.process(exchange, callback);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // start Salesforce processor
        ServiceHelper.startService(processor);
    }

    @Override
    protected void doStop() throws Exception {
        // stop Salesforce processor
        ServiceHelper.stopService(processor);

        super.doStop();
    }

}
