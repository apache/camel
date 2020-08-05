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
package org.apache.camel.component.aws2.sts;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest.Builder;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;

/**
 * A Producer which sends messages to the Amazon ECS Service SDK v2
 * <a href="http://aws.amazon.com/ecs/">AWS ECS</a>
 */
public class STS2Producer extends DefaultProducer {

	private static final Logger LOG = LoggerFactory.getLogger(STS2Producer.class);

	private transient String ecsProducerToString;

	public STS2Producer(Endpoint endpoint) {
		super(endpoint);
	}

	@Override
	public void process(Exchange exchange) throws Exception {
		switch (determineOperation(exchange)) {
		case assumeRole:
			assumeRole(getEndpoint().getStsClient(), exchange);
			break;
		default:
			throw new IllegalArgumentException("Unsupported operation");
		}
	}

	private STS2Operations determineOperation(Exchange exchange) {
		STS2Operations operation = exchange.getIn().getHeader(STS2Constants.OPERATION, STS2Operations.class);
		if (operation == null) {
			operation = getConfiguration().getOperation();
		}
		return operation;
	}

	protected STS2Configuration getConfiguration() {
		return getEndpoint().getConfiguration();
	}

	@Override
	public String toString() {
		if (ecsProducerToString == null) {
			ecsProducerToString = "ECSProducer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
		}
		return ecsProducerToString;
	}

	@Override
	public STS2Endpoint getEndpoint() {
		return (STS2Endpoint) super.getEndpoint();
	}

	private void assumeRole(StsClient stsClient, Exchange exchange) throws InvalidPayloadException {
		if (getConfiguration().isPojoRequest()) {
			Object payload = exchange.getIn().getMandatoryBody();
			if (payload instanceof AssumeRoleRequest) {
				AssumeRoleResponse result;
				try {
					AssumeRoleRequest request = (AssumeRoleRequest) payload;
					result = stsClient.assumeRole(request);
				} catch (AwsServiceException ase) {
					LOG.trace("Assume Role command returned the error code {}", ase.awsErrorDetails().errorCode());
					throw ase;
				}
				Message message = getMessageForResponse(exchange);
				message.setBody(result);
			}
		} else {
			Builder builder = AssumeRoleRequest.builder();
			if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(STS2Constants.MAX_RESULTS))) {
				int maxRes = exchange.getIn().getHeader(STS2Constants.MAX_RESULTS, Integer.class);
				builder.maxResults(maxRes);
			}
			ListClustersResponse result;
			try {
				ListClustersRequest request = builder.build();
				result = ecsClient.listClusters(request);
			} catch (AwsServiceException ase) {
				LOG.trace("List Clusters command returned the error code {}", ase.awsErrorDetails().errorCode());
				throw ase;
			}
			Message message = getMessageForResponse(exchange);
			message.setBody(result);
		}
	}
	
	public static Message getMessageForResponse(final Exchange exchange) {
		return exchange.getMessage();
	}
}
