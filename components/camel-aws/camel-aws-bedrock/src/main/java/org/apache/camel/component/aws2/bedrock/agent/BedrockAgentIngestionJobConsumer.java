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
import org.apache.camel.Processor;
import org.apache.camel.support.ScheduledPollConsumer;
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.services.bedrockagent.BedrockAgentClient;
import software.amazon.awssdk.services.bedrockagent.model.GetIngestionJobRequest;
import software.amazon.awssdk.services.bedrockagent.model.GetIngestionJobResponse;

public class BedrockAgentIngestionJobConsumer extends ScheduledPollConsumer {

    protected final BedrockAgentEndpoint endpoint;

    public BedrockAgentIngestionJobConsumer(BedrockAgentEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    protected int poll() throws Exception {
        GetIngestionJobRequest.Builder builder = GetIngestionJobRequest.builder();
        if (ObjectHelper.isNotEmpty(getEndpoint().getConfiguration().getKnowledgeBaseId())) {
            builder.knowledgeBaseId(getEndpoint().getConfiguration().getKnowledgeBaseId());
        } else {
            throw new IllegalArgumentException("KnowledgeBaseId must be specified");
        }
        if (ObjectHelper.isNotEmpty(getEndpoint().getConfiguration().getDataSourceId())) {
            builder.dataSourceId(getEndpoint().getConfiguration().getDataSourceId());
        } else {
            throw new IllegalArgumentException("DataSourceId must be specified");
        }
        if (ObjectHelper.isNotEmpty(getEndpoint().getConfiguration().getIngestionJobId())) {
            builder.ingestionJobId(getEndpoint().getConfiguration().getIngestionJobId());
        } else {
            throw new IllegalArgumentException("IngestionJobId must be specified");
        }

        GetIngestionJobResponse response = getClient().getIngestionJob(builder.build());
        getProcessor().process(createExchange(response));
        return 1;
    }

    private BedrockAgentClient getClient() {
        return getEndpoint().getBedrockAgentClient();
    }

    @Override
    public BedrockAgentEndpoint getEndpoint() {
        return (BedrockAgentEndpoint) super.getEndpoint();
    }

    protected Exchange createExchange(GetIngestionJobResponse response) {
        Exchange exchange = createExchange(true);
        exchange.getMessage().setBody(response.ingestionJob());
        exchange.getMessage()
                .setHeader(
                        BedrockAgentConstants.INGESTION_JOB_STATUS,
                        response.ingestionJob().status());
        if (response.ingestionJob().hasFailureReasons()) {
            exchange.getMessage()
                    .setHeader(
                            BedrockAgentConstants.INGESTION_JOB_FAILURE_REASONS,
                            response.ingestionJob().failureReasons());
        }
        return exchange;
    }
}
