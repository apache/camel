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

import com.openai.models.responses.Response;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.ai.observability.GenAiErrorSupport;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;

/**
 * OpenAI producer for the {@code responses-retrieve} and {@code responses-cancel} operations, which retrieve or cancel
 * a stored Responses API response, such as one created with {@code background=true}.
 */
public class OpenAIResponsesStoredProducer extends DefaultProducer {

    private final boolean cancel;

    public OpenAIResponsesStoredProducer(OpenAIEndpoint endpoint, boolean cancel) {
        super(endpoint);
        this.cancel = cancel;
    }

    @Override
    public OpenAIEndpoint getEndpoint() {
        return (OpenAIEndpoint) super.getEndpoint();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String responseId = exchange.getIn().getHeader(OpenAIConstants.RESPONSE_ID, String.class);
        if (ObjectHelper.isEmpty(responseId)) {
            throw new IllegalArgumentException(
                    "The " + OpenAIConstants.RESPONSE_ID + " header must hold the id of the response to "
                                               + (cancel ? "cancel" : "retrieve"));
        }

        Response response;
        try {
            response = cancel
                    ? getEndpoint().getClient().responses().cancel(responseId)
                    : getEndpoint().getClient().responses().retrieve(responseId);
        } catch (RuntimeException e) {
            GenAiErrorSupport.apply(exchange, e);
            throw e;
        }

        if (getEndpoint().getConfiguration().isStoreFullResponse()) {
            exchange.setProperty(OpenAIConstants.RESPONSES_RESPONSE, response);
        }
        OpenAIResponsesSupport.requireNoPendingMcpApprovals(exchange, response);
        Message out = exchange.getMessage();
        out.setBody(OpenAIResponsesSupport.extractAssistantText(response));
        OpenAIResponsesProducer.setResponseHeaders(out, response);
    }
}
