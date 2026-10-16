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
package org.apache.camel.component.openai;

import com.openai.models.decisions.Decision;
import com.openai.models.decisions.DecisionCreateParams;
import org.apache.camel.Exchange;
import org.apache.camel.component.ai.observability.GenAiObservation;
import org.apache.camel.component.ai.observability.GenAiOperationName;
import org.apache.camel.component.ai.observability.GenAiUsage;
import org.apache.camel.support.DefaultProducer;

/** Sends native Decisions requests without executing tools or applying decision policy. */
public class OpenAIDecisionsProducer extends DefaultProducer {
    public OpenAIDecisionsProducer(OpenAIEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        if (!(exchange.getMessage().getBody() instanceof DecisionCreateParams request)) {
            throw new IllegalArgumentException("Decisions requires a DecisionCreateParams body");
        }
        GenAiObservation observation
                = OpenAIGenAiProducerSupport.start(exchange, GenAiOperationName.DECISIONS, request.model());
        try {
            Decision response = ((OpenAIEndpoint) getEndpoint()).decide(request);
            var usage = response._usage().asKnown();
            OpenAIGenAiProducerSupport.recordSuccess(observation,
                    GenAiUsage.of(usage.map(Decision.Usage::inputTokens).orElse(null),
                            usage.map(Decision.Usage::outputTokens).orElse(null), null, response.model()));
            exchange.getMessage().setBody(response);
        } catch (Exception e) {
            OpenAIGenAiProducerSupport.recordFailure(exchange, observation, e);
            throw e;
        } finally {
            observation.close();
        }
    }
}
