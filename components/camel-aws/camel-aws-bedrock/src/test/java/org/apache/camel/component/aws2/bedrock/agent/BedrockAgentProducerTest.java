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
package org.apache.camel.component.aws2.bedrock.agent;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.bedrockagent.BedrockAgentClient;
import software.amazon.awssdk.services.bedrockagent.model.GetIngestionJobRequest;
import software.amazon.awssdk.services.bedrockagent.model.GetIngestionJobResponse;
import software.amazon.awssdk.services.bedrockagent.model.IngestionJob;
import software.amazon.awssdk.services.bedrockagent.model.IngestionJobStatus;
import software.amazon.awssdk.services.bedrockagent.model.StartIngestionJobRequest;
import software.amazon.awssdk.services.bedrockagent.model.StartIngestionJobResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BedrockAgentProducerTest {

    private final BedrockAgentClient client = mock(BedrockAgentClient.class);

    private BedrockAgentProducer createProducer(BedrockAgentConfiguration configuration) {
        BedrockAgentEndpoint endpoint = mock(BedrockAgentEndpoint.class);
        when(endpoint.getConfiguration()).thenReturn(configuration);
        when(endpoint.getBedrockAgentClient()).thenReturn(client);
        return new BedrockAgentProducer(endpoint);
    }

    @Test
    void noOperationFailsWithHint() {
        BedrockAgentProducer producer = createProducer(new BedrockAgentConfiguration());
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());

        assertThatThrownBy(() -> producer.process(exchange))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Operation must be specified, either with the operation option or the CamelAwsBedrockAgentOperation header");
        verifyNoInteractions(client);
    }

    @Test
    void getIngestionJobSetsStatusHeaderAsEnum() throws Exception {
        when(client.getIngestionJob(any(GetIngestionJobRequest.class))).thenReturn(GetIngestionJobResponse.builder()
                .ingestionJob(IngestionJob.builder().ingestionJobId("job-1").status(IngestionJobStatus.COMPLETE).build())
                .build());
        BedrockAgentConfiguration configuration = new BedrockAgentConfiguration();
        configuration.setKnowledgeBaseId("kb-1");
        configuration.setDataSourceId("ds-1");
        configuration.setIngestionJobId("job-1");
        BedrockAgentProducer producer = createProducer(configuration);
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getIn().setHeader(BedrockAgentConstants.OPERATION, BedrockAgentOperations.getIngestionJob);

        producer.process(exchange);

        assertThat(exchange.getMessage().getHeader(BedrockAgentConstants.INGESTION_JOB_STATUS))
                .isEqualTo(IngestionJobStatus.COMPLETE);
        assertThat(exchange.getMessage().getBody(IngestionJob.class).ingestionJobId()).isEqualTo("job-1");
    }

    @Test
    void headersOverrideTheOptions() throws Exception {
        when(client.getIngestionJob(any(GetIngestionJobRequest.class))).thenReturn(GetIngestionJobResponse.builder()
                .ingestionJob(IngestionJob.builder().ingestionJobId("job-2").status(IngestionJobStatus.COMPLETE).build())
                .build());
        BedrockAgentConfiguration configuration = new BedrockAgentConfiguration();
        configuration.setKnowledgeBaseId("kb-1");
        configuration.setDataSourceId("ds-1");
        configuration.setIngestionJobId("job-1");
        BedrockAgentProducer producer = createProducer(configuration);
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getIn().setHeader(BedrockAgentConstants.OPERATION, BedrockAgentOperations.getIngestionJob);
        exchange.getIn().setHeader(BedrockAgentConstants.KNOWLEDGE_BASE_ID, "kb-2");
        exchange.getIn().setHeader(BedrockAgentConstants.DATASOURCE_ID, "ds-2");
        exchange.getIn().setHeader(BedrockAgentConstants.INGESTION_JOB_ID, "job-2");

        producer.process(exchange);

        ArgumentCaptor<GetIngestionJobRequest> request = ArgumentCaptor.forClass(GetIngestionJobRequest.class);
        verify(client).getIngestionJob(request.capture());
        assertThat(request.getValue().knowledgeBaseId()).isEqualTo("kb-2");
        assertThat(request.getValue().dataSourceId()).isEqualTo("ds-2");
        assertThat(request.getValue().ingestionJobId()).isEqualTo("job-2");
    }

    @Test
    void optionsAreUsedWithoutHeaders() throws Exception {
        when(client.startIngestionJob(any(StartIngestionJobRequest.class))).thenReturn(StartIngestionJobResponse.builder()
                .ingestionJob(IngestionJob.builder().ingestionJobId("job-3").build())
                .build());
        BedrockAgentConfiguration configuration = new BedrockAgentConfiguration();
        configuration.setKnowledgeBaseId("kb-1");
        configuration.setDataSourceId("ds-1");
        BedrockAgentProducer producer = createProducer(configuration);
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getIn().setHeader(BedrockAgentConstants.OPERATION, BedrockAgentOperations.startIngestionJob);

        producer.process(exchange);

        ArgumentCaptor<StartIngestionJobRequest> request = ArgumentCaptor.forClass(StartIngestionJobRequest.class);
        verify(client).startIngestionJob(request.capture());
        assertThat(request.getValue().knowledgeBaseId()).isEqualTo("kb-1");
        assertThat(request.getValue().dataSourceId()).isEqualTo("ds-1");
        assertThat(exchange.getMessage().getBody()).isEqualTo("job-3");
    }

    @Test
    void missingIdFails() {
        BedrockAgentConfiguration configuration = new BedrockAgentConfiguration();
        configuration.setKnowledgeBaseId("kb-1");
        BedrockAgentProducer producer = createProducer(configuration);
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getIn().setHeader(BedrockAgentConstants.OPERATION, BedrockAgentOperations.listIngestionJobs);

        assertThatThrownBy(() -> producer.process(exchange))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("DataSourceId must be specified");
        verifyNoInteractions(client);
    }
}
