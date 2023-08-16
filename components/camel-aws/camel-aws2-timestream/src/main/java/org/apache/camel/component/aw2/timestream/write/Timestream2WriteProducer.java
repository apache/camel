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
package org.apache.camel.component.aw2.timestream.write;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.component.aw2.timestream.Timestream2Configuration;
import org.apache.camel.component.aw2.timestream.Timestream2Constants;
import org.apache.camel.component.aw2.timestream.Timestream2Operations;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.health.WritableHealthCheckRepository;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.timestreamwrite.TimestreamWriteClient;
import software.amazon.awssdk.services.timestreamwrite.model.DescribeEndpointsRequest;
import software.amazon.awssdk.services.timestreamwrite.model.DescribeEndpointsResponse;

/**
 * A Producer which sends messages to the Amazon Web Service Timestream <a href="https://aws.amazon.com/timestream/">AWS
 * Timestream</a>
 */
public class Timestream2WriteProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(Timestream2WriteProducer.class);

    private transient String timestreamWriteProducerToString;

    private HealthCheck producerHealthCheck;
    private WritableHealthCheckRepository healthCheckRepository;

    public Timestream2WriteProducer(final Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(final Exchange exchange) throws Exception {
        switch (determineOperation(exchange)) {
            case describeEndpoints -> describeEndpoints(getEndpoint().getAwsTimestreamWriteClient(), exchange);
            case createBatchLoadTask -> createBatchLoadTask(getEndpoint().getAwsTimestreamWriteClient(), exchange);
            case describeBatchLoadTask -> describeBatchLoadTask(getEndpoint().getAwsTimestreamWriteClient(), exchange);
            case resumeBatchLoadTask -> resumeBatchLoadTask(getEndpoint().getAwsTimestreamWriteClient(), exchange);
            case listBatchLoadTasks -> listBatchLoadTasks(getEndpoint().getAwsTimestreamWriteClient(), exchange);
            case createDatabase -> createDatabase(getEndpoint().getAwsTimestreamWriteClient(), exchange);
            case deleteDatabase -> deleteDatabase(getEndpoint().getAwsTimestreamWriteClient(), exchange);
            case describeDatabase -> describeDatabase(getEndpoint().getAwsTimestreamWriteClient(), exchange);
            case updateDatabase -> updateDatabase(getEndpoint().getAwsTimestreamWriteClient(), exchange);
            case listDatabases -> listDatabases(getEndpoint().getAwsTimestreamWriteClient(), exchange);
            case createTable -> createTable(getEndpoint().getAwsTimestreamWriteClient(), exchange);
            case deleteTable -> deleteTable(getEndpoint().getAwsTimestreamWriteClient(), exchange);
            case describeTable -> describeTable(getEndpoint().getAwsTimestreamWriteClient(), exchange);
            case updateTable -> updateTable(getEndpoint().getAwsTimestreamWriteClient(), exchange);
            case listTables -> listTables(getEndpoint().getAwsTimestreamWriteClient(), exchange);
            case writeRecords -> writeRecords(getEndpoint().getAwsTimestreamWriteClient(), exchange);
            default -> throw new IllegalArgumentException("Unsupported operation");
        }
    }

    private Timestream2Operations determineOperation(Exchange exchange) {
        Timestream2Operations operation
                = exchange.getIn().getHeader(Timestream2Constants.OPERATION, Timestream2Operations.class);
        if (operation == null) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }

    protected Timestream2Configuration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public String toString() {
        if (timestreamWriteProducerToString == null) {
            timestreamWriteProducerToString
                    = "TimestreamWriteProducer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return timestreamWriteProducerToString;
    }

    @Override
    public Timestream2WriteEndpoint getEndpoint() {
        return (Timestream2WriteEndpoint) super.getEndpoint();
    }

    private void describeEndpoints(TimestreamWriteClient timestreamWriteClient, Exchange exchange)
            throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DescribeEndpointsRequest request) {
                DescribeEndpointsResponse result;
                try {
                    result = timestreamWriteClient.describeEndpoints(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Describe Endpoints command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            DescribeEndpointsRequest.Builder builder = DescribeEndpointsRequest.builder();

            DescribeEndpointsResponse result;
            try {
                result = timestreamWriteClient.describeEndpoints(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Describe Endpoints command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void createBatchLoadTask(TimestreamWriteClient timestreamWriteClient, Exchange exchange) {}
    private void describeBatchLoadTask(TimestreamWriteClient timestreamWriteClient, Exchange exchange) {}
    private void resumeBatchLoadTask(TimestreamWriteClient timestreamWriteClient, Exchange exchange) {}
    private void listBatchLoadTasks(TimestreamWriteClient timestreamWriteClient, Exchange exchange) {}
    private void createDatabase(TimestreamWriteClient timestreamWriteClient, Exchange exchange) {}
    private void deleteDatabase(TimestreamWriteClient timestreamWriteClient, Exchange exchange) {}
    private void describeDatabase(TimestreamWriteClient timestreamWriteClient, Exchange exchange) {}
    private void updateDatabase(TimestreamWriteClient timestreamWriteClient, Exchange exchange) {}
    private void listDatabases(TimestreamWriteClient timestreamWriteClient, Exchange exchange) {}
    private void createTable(TimestreamWriteClient timestreamWriteClient, Exchange exchange) {}
    private void deleteTable(TimestreamWriteClient timestreamWriteClient, Exchange exchange) {}
    private void describeTable(TimestreamWriteClient timestreamWriteClient, Exchange exchange) {}
    private void updateTable(TimestreamWriteClient timestreamWriteClient, Exchange exchange) {}
    private void listTables(TimestreamWriteClient timestreamWriteClient, Exchange exchange) {}
    private void writeRecords(TimestreamWriteClient timestreamWriteClient, Exchange exchange) {}

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
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
            producerHealthCheck = new Timestream2WriteProducerHealthCheck(getEndpoint(), id);
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
