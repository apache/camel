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
package org.apache.camel.component.aws2.ddb;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.health.WritableHealthCheckRepository;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.URISupport;

/**
 * A Producer which stores data into the Amazon DynamoDB Service <a href="http://aws.amazon.com/dynamodb/">AWS
 * DynamoDB</a>
 */
public class Ddb2Producer extends DefaultProducer {

    private transient String ddbProducerToString;
    private HealthCheck producerHealthCheck;
    private WritableHealthCheckRepository healthCheckRepository;

    public Ddb2Producer(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        switch (determineOperation(exchange)) {
            case BatchGetItems:
                new BatchGetItemsCommand(getEndpoint().getDdbClient(), getConfiguration(), exchange).execute();
                break;
            case DeleteItem:
                new DeleteItemCommand(getEndpoint().getDdbClient(), getConfiguration(), exchange).execute();
                break;
            case DeleteTable:
                new DeleteTableCommand(getEndpoint().getDdbClient(), getConfiguration(), exchange).execute();
                break;
            case DescribeTable:
                new DescribeTableCommand(getEndpoint().getDdbClient(), getConfiguration(), exchange).execute();
                break;
            case GetItem:
                new GetItemCommand(getEndpoint().getDdbClient(), getConfiguration(), exchange).execute();
                break;
            case PutItem:
                new PutItemCommand(getEndpoint().getDdbClient(), getConfiguration(), exchange).execute();
                break;
            case Query:
                new QueryCommand(getEndpoint().getDdbClient(), getConfiguration(), exchange).execute();
                break;
            case Scan:
                new ScanCommand(getEndpoint().getDdbClient(), getConfiguration(), exchange).execute();
                break;
            case UpdateItem:
                new UpdateItemCommand(getEndpoint().getDdbClient(), getConfiguration(), exchange).execute();
                break;
            case UpdateTable:
                new UpdateTableCommand(getEndpoint().getDdbClient(), getConfiguration(), exchange).execute();
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation");
        }
    }

    private Ddb2Operations determineOperation(Exchange exchange) {
        Ddb2Operations operation = exchange.getIn().getHeader(Ddb2Constants.OPERATION, Ddb2Operations.class);
        return operation != null ? operation : getConfiguration().getOperation();
    }

    protected Ddb2Configuration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public String toString() {
        if (ddbProducerToString == null) {
            ddbProducerToString = "DdbProducer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return ddbProducerToString;
    }

    @Override
    public Ddb2Endpoint getEndpoint() {
        return (Ddb2Endpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        // health-check is optional so discover and resolve
        healthCheckRepository = HealthCheckHelper.getHealthCheckRepository(
                getEndpoint().getCamelContext(),
                "producers",
                WritableHealthCheckRepository.class);

        if (healthCheckRepository != null) {
            String id = getEndpoint().getId();
            producerHealthCheck = new Db2ProducerHealthCheck(getEndpoint(), id);
            producerHealthCheck.setEnabled(getEndpoint().getComponent().isHealthCheckProducerEnabled());
            healthCheckRepository.addHealthCheck(producerHealthCheck);
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (healthCheckRepository != null && producerHealthCheck != null) {
            healthCheckRepository.removeHealthCheck(producerHealthCheck);
            producerHealthCheck = null;
        }
    }

}
