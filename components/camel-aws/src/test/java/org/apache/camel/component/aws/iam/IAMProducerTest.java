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
package org.apache.camel.component.aws.iam;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import com.amazonaws.services.identitymanagement.model.ListAccessKeysResult;

public class IAMProducerTest extends CamelTestSupport {

	@EndpointInject(uri = "mock:result")
	private MockEndpoint mock;

	@Test
	public void iamListKeysTest() throws Exception {

		mock.expectedMessageCount(1);
		Exchange exchange = template.request("direct:listKeys", new Processor() {
			@Override
			public void process(Exchange exchange) throws Exception {
				exchange.getIn().setHeader(IAMConstants.OPERATION, IAMOperations.listAccessKeys);
			}
		});

		assertMockEndpointsSatisfied();

		ListAccessKeysResult resultGet = (ListAccessKeysResult) exchange.getIn().getBody();
		assertEquals(1, resultGet.getAccessKeyMetadata().size());
		assertEquals("1", resultGet.getAccessKeyMetadata().get(0).getAccessKeyId());
	}

	@Override
	protected JndiRegistry createRegistry() throws Exception {
		JndiRegistry registry = super.createRegistry();

		AmazonIAMClientMock clientMock = new AmazonIAMClientMock();

		registry.bind("amazonIAMClient", clientMock);

		return registry;
	}

	@Override
	protected RouteBuilder createRouteBuilder() throws Exception {
		return new RouteBuilder() {
			@Override
			public void configure() throws Exception {
				from("direct:listKeys").to("aws-iam://test?iamClient=#amazonIAMClient&operation=listAccessKeys")
						.to("mock:result");
			}
		};
	}
}