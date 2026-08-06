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
package org.apache.camel.component.aws.parameterstore;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.PutParameterRequest;
import software.amazon.awssdk.services.ssm.model.PutParameterResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class ParameterStoreProducerTest {

    @Test
    void putParameterHonorsTheValueHeader() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            SsmClient client = Mockito.mock(SsmClient.class);
            ArgumentCaptor<PutParameterRequest> captor = ArgumentCaptor.forClass(PutParameterRequest.class);
            when(client.putParameter(captor.capture())).thenReturn(PutParameterResponse.builder().build());

            ParameterStoreConfiguration configuration = new ParameterStoreConfiguration();
            configuration.setSsmClient(client);
            ParameterStoreEndpoint endpoint
                    = new ParameterStoreEndpoint("aws-parameter-store://test", null, configuration);
            endpoint.setCamelContext(context);
            endpoint.start();

            ParameterStoreProducer producer = new ParameterStoreProducer(endpoint);

            Exchange exchange = new DefaultExchange(context);
            exchange.getIn().setHeader(ParameterStoreConstants.OPERATION, ParameterStoreOperations.putParameter);
            exchange.getIn().setHeader(ParameterStoreConstants.PARAMETER_NAME, "myParam");
            exchange.getIn().setHeader(ParameterStoreConstants.PARAMETER_VALUE, "valueFromHeader");
            exchange.getIn().setBody("valueFromBody");

            producer.process(exchange);

            // the CamelAwsParameterStoreValue header used to be ignored (the value always came from the body);
            // it must now be honored when present
            assertThat(captor.getValue().value()).isEqualTo("valueFromHeader");
        }
    }
}
