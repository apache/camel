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
package org.apache.camel.component.aws2.redshiftdata;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.health.WritableHealthCheckRepository;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.redshiftdata.RedshiftDataClient;
import software.amazon.awssdk.services.redshiftdata.model.ListDatabasesRequest;
import software.amazon.awssdk.services.redshiftdata.model.ListDatabasesResponse;

/**
 * A Producer to perform to perform actions on AWS Redshift through Redshift Data
 * <a href="https://docs.aws.amazon.com/redshift-data/latest/APIReference">Amazon Redshift Data API</a>
 */
public class RedshiftData2Producer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(RedshiftData2Producer.class);

    private transient String redshiftDataProducerToString;

    private HealthCheck producerHealthCheck;
    private WritableHealthCheckRepository healthCheckRepository;

    public RedshiftData2Producer(final Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(final Exchange exchange) throws Exception {
        switch (determineOperation(exchange)) {
            case listDatabases -> listDatabases(getConfiguration().getAwsRedshiftDataClient(), exchange);
            default -> throw new IllegalArgumentException("Unsupported operation");
        }
    }

    private RedshiftData2Operations determineOperation(Exchange exchange) {
        RedshiftData2Operations operation
                = exchange.getIn().getHeader(RedshiftData2Constants.OPERATION, RedshiftData2Operations.class);
        if (operation == null) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }

    protected RedshiftData2Configuration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public String toString() {
        if (redshiftDataProducerToString == null) {
            redshiftDataProducerToString
                    = "RedshiftDataProducer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return redshiftDataProducerToString;
    }

    @Override
    public RedshiftData2Endpoint getEndpoint() {
        return (RedshiftData2Endpoint) super.getEndpoint();
    }

    private void listDatabases(RedshiftDataClient redshiftDataClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof ListDatabasesRequest request) {
                ListDatabasesResponse result;
                try {
                    result = redshiftDataClient.listDatabases(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("List Redshift Databases command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            ListDatabasesRequest.Builder builder = ListDatabasesRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.CLUSTER_IDENTIFIER))) {
                String clusterIdentifier = exchange.getIn().getHeader(RedshiftData2Constants.CLUSTER_IDENTIFIER, String.class);
                builder.clusterIdentifier(clusterIdentifier);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.DATABASE))) {
                String database = exchange.getIn().getHeader(RedshiftData2Constants.DATABASE, String.class);
                builder.database(database);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.DB_USER))) {
                String dbUser = exchange.getIn().getHeader(RedshiftData2Constants.DB_USER, String.class);
                builder.dbUser(dbUser);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.SECRET_ARN))) {
                String secretArn = exchange.getIn().getHeader(RedshiftData2Constants.SECRET_ARN, String.class);
                builder.secretArn(secretArn);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.LIST_DATABASES_MAX_RESULTS))) {
                Integer maxResults
                        = exchange.getIn().getHeader(RedshiftData2Constants.LIST_DATABASES_MAX_RESULTS, Integer.class);
                builder.maxResults(maxResults);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.WORKGROUP_NAME))) {
                String workgroupName = exchange.getIn().getHeader(RedshiftData2Constants.WORKGROUP_NAME, String.class);
                builder.workgroupName(workgroupName);
            }

            ListDatabasesResponse result;
            try {
                result = redshiftDataClient.listDatabases(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("List Redshift Databases command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
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
            producerHealthCheck = new RedshiftData2ProducerHealthCheck(getEndpoint(), id);
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
