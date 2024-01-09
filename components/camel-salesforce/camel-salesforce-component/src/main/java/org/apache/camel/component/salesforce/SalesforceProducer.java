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
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.internal.OperationName;
import org.apache.camel.component.salesforce.internal.SalesforceSession;
import org.apache.camel.component.salesforce.internal.processor.AnalyticsApiProcessor;
import org.apache.camel.component.salesforce.internal.processor.BulkApiProcessor;
import org.apache.camel.component.salesforce.internal.processor.BulkApiV2Processor;
import org.apache.camel.component.salesforce.internal.processor.CompositeApiProcessor;
import org.apache.camel.component.salesforce.internal.processor.CompositeSObjectCollectionsProcessor;
import org.apache.camel.component.salesforce.internal.processor.JsonRestProcessor;
import org.apache.camel.component.salesforce.internal.processor.PubSubApiProcessor;
import org.apache.camel.component.salesforce.internal.processor.RawProcessor;
import org.apache.camel.component.salesforce.internal.processor.SalesforceProcessor;
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

        // check if it's a Bulk Operation
        final OperationName operationName = endpoint.getOperationName();
        if (isBulkOperation(operationName)) {
            processor = new BulkApiProcessor(endpoint);
        } else if (isBulkV2Operation(operationName)) {
            processor = new BulkApiV2Processor(endpoint);
        } else if (isAnalyticsOperation(operationName)) {
            processor = new AnalyticsApiProcessor(endpoint);
        } else if (isCompositeOperation(operationName)) {
            processor = new CompositeApiProcessor(endpoint);
        } else if (isCompositeSObjectCollectionsOperation(operationName)) {
            processor = new CompositeSObjectCollectionsProcessor(endpoint);
        } else if (isRawOperation(operationName)) {
            processor = new RawProcessor(endpoint);
        } else if (isPubSubOperation(operationName)) {
            processor = new PubSubApiProcessor(endpoint);
        } else {
            processor = new JsonRestProcessor(endpoint);
        }
    }

    private boolean isBulkV2Operation(OperationName operationName) {
        switch (operationName) {
            case BULK2_CREATE_JOB:
            case BULK2_CREATE_BATCH:
            case BULK2_CLOSE_JOB:
            case BULK2_GET_JOB:
            case BULK2_ABORT_JOB:
            case BULK2_DELETE_JOB:
            case BULK2_GET_SUCCESSFUL_RESULTS:
            case BULK2_GET_FAILED_RESULTS:
            case BULK2_GET_UNPROCESSED_RECORDS:
            case BULK2_GET_ALL_JOBS:
            case BULK2_CREATE_QUERY_JOB:
            case BULK2_GET_QUERY_JOB:
            case BULK2_GET_QUERY_JOB_RESULTS:
            case BULK2_ABORT_QUERY_JOB:
            case BULK2_DELETE_QUERY_JOB:
            case BULK2_GET_ALL_QUERY_JOBS:
                return true;
            default:
                return false;
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
            case COMPOSITE:
            case COMPOSITE_BATCH:
            case COMPOSITE_TREE:
                return true;
            default:
                return false;
        }
    }

    private static boolean isCompositeSObjectCollectionsOperation(OperationName operationName) {
        switch (operationName) {
            case COMPOSITE_CREATE_SOBJECT_COLLECTIONS:
            case COMPOSITE_UPDATE_SOBJECT_COLLECTIONS:
            case COMPOSITE_UPSERT_SOBJECT_COLLECTIONS:
            case COMPOSITE_RETRIEVE_SOBJECT_COLLECTIONS:
            case COMPOSITE_DELETE_SOBJECT_COLLECTIONS:
                return true;
            default:
                return false;
        }
    }

    private static boolean isRawOperation(OperationName operationName) {
        return operationName == OperationName.RAW;
    }

    private static boolean isPubSubOperation(OperationName operationName) {
        return operationName == OperationName.PUBSUB_PUBLISH;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        SalesforceEndpoint endpoint = (SalesforceEndpoint) getEndpoint();
        SalesforceSession session = endpoint.getComponent().getSession();
        if (session != null && session.getAccessToken() == null) {
            try {
                session.login(null);
            } catch (SalesforceException e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        }

        LOG.debug("Processing {}", endpoint.getOperationName());
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
