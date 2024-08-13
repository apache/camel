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
package org.apache.camel.reifier;

import org.apache.camel.Endpoint;
import org.apache.camel.Expression;
import org.apache.camel.LineNumberAware;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.PollDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.processor.PollEnricher;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.LanguageSupport;

public class PollReifier extends ProcessorReifier<PollDefinition> {

    public PollReifier(Route route, ProcessorDefinition<?> definition) {
        super(route, (PollDefinition) definition);
    }

    @Override
    public Processor createProcessor() throws Exception {
        Endpoint endpoint = resolveEndpoint();
        String uri = endpoint.getEndpointUri();
        boolean simple = LanguageSupport.hasSimpleFunction(uri);
        Expression exp;
        if (simple) {
            exp = camelContext.resolveLanguage("simple").createExpression(uri);
        } else {
            exp = camelContext.resolveLanguage("constant").createExpression(uri);
        }
        long timeout = parseDuration(definition.getTimeout(), 20000);
        PollEnricher answer = new PollEnricher(exp, uri, timeout);
        answer.setVariableReceive(parseString(definition.getVariableReceive()));
        return answer;
    }

    public Endpoint resolveEndpoint() {
        Endpoint answer;
        if (definition.getEndpoint() == null) {
            if (definition.getEndpointConsumerBuilder() == null) {
                answer = CamelContextHelper.resolveEndpoint(camelContext, definition.getEndpointUri(), null);
            } else {
                answer = definition.getEndpointConsumerBuilder().resolve(camelContext);
            }
        } else {
            answer = definition.getEndpoint();
        }
        LineNumberAware.trySetLineNumberAware(answer, definition);
        return answer;
    }

}
