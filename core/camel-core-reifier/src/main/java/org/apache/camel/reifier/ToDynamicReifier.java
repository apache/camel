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

import org.apache.camel.ExchangePattern;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.ProcessorDefinitionHelper;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.ToDynamicDefinition;
import org.apache.camel.processor.SendDynamicProcessor;
import org.apache.camel.spi.Language;
import org.apache.camel.support.EndpointHelper;
import org.apache.camel.util.StringHelper;

public class ToDynamicReifier<T extends ToDynamicDefinition> extends ProcessorReifier<T> {

    public ToDynamicReifier(Route route, ProcessorDefinition<?> definition) {
        super(route, (T) definition);
    }

    @Override
    public Processor createProcessor() throws Exception {
        String uri;
        Expression exp;
        if (definition.getEndpointProducerBuilder() != null) {
            uri = definition.getEndpointProducerBuilder().getRawUri();
            exp = definition.getEndpointProducerBuilder().expr(camelContext);
        } else {
            uri = StringHelper.notEmpty(definition.getUri(), "uri", this);
            exp = createExpression(uri);
        }

        // route templates should pre parse uri as they have dynamic values as part of their template parameters
        RouteDefinition rd = ProcessorDefinitionHelper.getRoute(definition);
        if (rd != null && rd.isTemplate() != null && rd.isTemplate()) {
            uri = EndpointHelper.resolveEndpointUriPropertyPlaceholders(camelContext, uri);
        }

        SendDynamicProcessor processor = new SendDynamicProcessor(uri, exp);
        processor.setCamelContext(camelContext);
        processor.setPattern(parse(ExchangePattern.class, definition.getPattern()));
        processor.setVariableSend(parseString(definition.getVariableSend()));
        processor.setVariableReceive(parseString(definition.getVariableReceive()));
        Integer num = parseInt(definition.getCacheSize());
        if (num != null) {
            processor.setCacheSize(num);
        }
        if (definition.getIgnoreInvalidEndpoint() != null) {
            processor.setIgnoreInvalidEndpoint(parseBoolean(definition.getIgnoreInvalidEndpoint(), false));
        }
        if (definition.getAllowOptimisedComponents() != null) {
            processor.setAllowOptimisedComponents(parseBoolean(definition.getAllowOptimisedComponents(), true));
        }
        if (definition.getAutoStartComponents() != null) {
            processor.setAutoStartupComponents(parseBoolean(definition.getAutoStartComponents(), true));
        }
        return processor;
    }

    protected Expression createExpression(String uri) {
        // make sure to parse property placeholders
        uri = EndpointHelper.resolveEndpointUriPropertyPlaceholders(camelContext, uri);

        // we use simple language by default, but you can configure a different language
        String language = "simple";
        if (uri.startsWith("language:")) {
            String value = StringHelper.after(uri, "language:");
            language = StringHelper.before(value, ":");
            uri = StringHelper.after(value, ":");
        }

        Language lan = camelContext.resolveLanguage(language);
        return lan.createExpression(uri);
    }

}
