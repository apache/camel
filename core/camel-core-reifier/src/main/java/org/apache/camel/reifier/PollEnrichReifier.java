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

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.PollEnrichDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.ProcessorDefinitionHelper;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.language.ConstantExpression;
import org.apache.camel.processor.PollEnricher;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.EndpointHelper;

public class PollEnrichReifier extends ProcessorReifier<PollEnrichDefinition> {

    public PollEnrichReifier(Route route, ProcessorDefinition<?> definition) {
        super(route, (PollEnrichDefinition) definition);
    }

    @Override
    public Processor createProcessor() throws Exception {
        Expression exp;
        String uri;
        if (definition.getExpression() instanceof ConstantExpression) {
            exp = createExpression(definition.getExpression());
            Exchange ex = new DefaultExchange(camelContext);
            uri = exp.evaluate(ex, String.class);
        } else {
            exp = createExpression(definition.getExpression());
            uri = definition.getExpression().getExpression();
        }

        // route templates should pre parse uri as they have dynamic values as part of their template parameters
        RouteDefinition rd = ProcessorDefinitionHelper.getRoute(definition);
        if (rd != null && rd.isTemplate() != null && rd.isTemplate()) {
            uri = EndpointHelper.resolveEndpointUriPropertyPlaceholders(camelContext, uri);
        }

        // if no timeout then we should block, and there use a negative timeout
        long timeout = parseDuration(definition.getTimeout(), -1);

        PollEnricher enricher = new PollEnricher(exp, uri, timeout);
        AggregationStrategy strategy = getConfiguredAggregationStrategy(definition);
        if (strategy != null) {
            enricher.setAggregationStrategy(strategy);
        }
        Integer num = parseInt(definition.getCacheSize());
        if (num != null) {
            enricher.setCacheSize(num);
        }
        enricher.setVariableReceive(parseString(definition.getVariableReceive()));
        enricher.setIgnoreInvalidEndpoint(parseBoolean(definition.getIgnoreInvalidEndpoint(), false));
        enricher.setAggregateOnException(parseBoolean(definition.getAggregateOnException(), false));
        if (definition.getAutoStartComponents() != null) {
            enricher.setAutoStartupComponents(parseBoolean(definition.getAutoStartComponents(), true));
        }

        return enricher;
    }

}
