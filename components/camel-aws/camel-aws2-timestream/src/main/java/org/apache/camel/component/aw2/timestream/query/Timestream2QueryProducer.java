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
package org.apache.camel.component.aw2.timestream.query;

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
import software.amazon.awssdk.services.timestreamquery.TimestreamQueryClient;
import software.amazon.awssdk.services.timestreamquery.model.CreateScheduledQueryRequest;
import software.amazon.awssdk.services.timestreamquery.model.CreateScheduledQueryResponse;
import software.amazon.awssdk.services.timestreamquery.model.DescribeEndpointsRequest;
import software.amazon.awssdk.services.timestreamquery.model.DescribeEndpointsResponse;

/**
 * A Producer which sends messages to the Amazon Web Service Timestream <a href="https://aws.amazon.com/timestream/">AWS
 * Timestream</a>
 */
public class Timestream2QueryProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(Timestream2QueryProducer.class);

    private transient String timestreamQueryProducerToString;

    private HealthCheck producerHealthCheck;
    private WritableHealthCheckRepository healthCheckRepository;

    public Timestream2QueryProducer(final Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(final Exchange exchange) throws Exception {
        switch (determineOperation(exchange)) {
            case describeEndpoints -> describeEndpoints(getEndpoint().getAwsTimestreamQueryClient(), exchange);
            case createScheduledQuery -> createScheduledQuery(getEndpoint().getAwsTimestreamQueryClient(), exchange);
            case deleteScheduledQuery -> deleteScheduledQuery(getEndpoint().getAwsTimestreamQueryClient(), exchange);
            case executeScheduledQuery -> executeScheduledQuery(getEndpoint().getAwsTimestreamQueryClient(), exchange);
            case updateScheduledQuery -> updateScheduledQuery(getEndpoint().getAwsTimestreamQueryClient(), exchange);
            case listScheduledQueries -> listScheduledQueries(getEndpoint().getAwsTimestreamQueryClient(), exchange);
            case prepareQuery -> prepareQuery(getEndpoint().getAwsTimestreamQueryClient(), exchange);
            case query -> query(getEndpoint().getAwsTimestreamQueryClient(), exchange);
            case cancelQuery -> cancelQuery(getEndpoint().getAwsTimestreamQueryClient(), exchange);
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
        if (timestreamQueryProducerToString == null) {
            timestreamQueryProducerToString
                    = "TimestreamQueryProducer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return timestreamQueryProducerToString;
    }

    @Override
    public Timestream2QueryEndpoint getEndpoint() {
        return (Timestream2QueryEndpoint) super.getEndpoint();
    }

    private void describeEndpoints(TimestreamQueryClient timestreamQueryClient, Exchange exchange)
            throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DescribeEndpointsRequest request) {
                DescribeEndpointsResponse result;
                try {
                    result = timestreamQueryClient.describeEndpoints(request);
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
                result = timestreamQueryClient.describeEndpoints(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Describe Endpoints command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void createScheduledQuery(TimestreamQueryClient timestreamQueryClient, Exchange exchange)
            throws InvalidPayloadException {
    }

    private void deleteScheduledQuery(TimestreamQueryClient timestreamQueryClient, Exchange exchange)
            throws InvalidPayloadException {
    }

    private void executeScheduledQuery(TimestreamQueryClient timestreamQueryClient, Exchange exchange)
            throws InvalidPayloadException {
    }

    private void updateScheduledQuery(TimestreamQueryClient timestreamQueryClient, Exchange exchange)
            throws InvalidPayloadException {
    }

    private void listScheduledQueries(TimestreamQueryClient timestreamQueryClient, Exchange exchange)
            throws InvalidPayloadException {
    }

    private void prepareQuery(TimestreamQueryClient timestreamQueryClient, Exchange exchange) throws InvalidPayloadException {
    }

    private void query(TimestreamQueryClient timestreamQueryClient, Exchange exchange) throws InvalidPayloadException {
    }

    private void cancelQuery(TimestreamQueryClient timestreamQueryClient, Exchange exchange) throws InvalidPayloadException {
    }

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
            producerHealthCheck = new Timestream2QueryProducerHealthCheck(getEndpoint(), id);
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
