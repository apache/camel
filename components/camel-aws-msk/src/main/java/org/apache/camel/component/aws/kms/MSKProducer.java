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
package org.apache.camel.component.aws.kms;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.kafka.AWSKafka;
import com.amazonaws.services.kafka.model.ListClustersRequest;
import com.amazonaws.services.kafka.model.ListClustersResult;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;

/**
 * A Producer which sends messages to the Amazon MSK Service
 * <a href="http://aws.amazon.com/msk/">AWS MSK</a>
 */
public class MSKProducer extends DefaultProducer {

    private transient String mskProducerToString;

    public MSKProducer(Endpoint endpoint) {
        super(endpoint);
    }

    public void process(Exchange exchange) throws Exception {
        switch (determineOperation(exchange)) {
        case listKeys:
            listClusters(getEndpoint().getMskClient(), exchange);
            break;
        default:
            throw new IllegalArgumentException("Unsupported operation");
        }
    }

    private MSKOperations determineOperation(Exchange exchange) {
        MSKOperations operation = exchange.getIn().getHeader(MSKConstants.OPERATION, MSKOperations.class);
        if (operation == null) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }

    protected MSKConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public String toString() {
        if (mskProducerToString == null) {
            mskProducerToString = "MSKProducer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return mskProducerToString;
    }

    @Override
    public MSKEndpoint getEndpoint() {
        return (MSKEndpoint)super.getEndpoint();
    }

    private void listClusters(AWSKafka mskClient, Exchange exchange) {
        ListClustersRequest request = new ListClustersRequest();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(MSKConstants.CLUSTERS_FILTER))) {
            String filter = exchange.getIn().getHeader(MSKConstants.CLUSTERS_FILTER, String.class);
            request.withClusterNameFilter(filter);
        }
        ListClustersResult result;
        try {
            result = mskClient.listClusters(request);
        } catch (AmazonServiceException ase) {
            log.trace("List Clusters command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }
    
    public static Message getMessageForResponse(final Exchange exchange) {
        if (exchange.getPattern().isOutCapable()) {
            Message out = exchange.getOut();
            out.copyFrom(exchange.getIn());
            return out;
        }
        return exchange.getIn();
    }
}