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

import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.PollDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.processor.PollProcessor;
import org.apache.camel.spi.Language;
import org.apache.camel.support.EndpointHelper;
import org.apache.camel.support.LanguageSupport;
import org.apache.camel.util.StringHelper;

public class PollReifier extends ProcessorReifier<PollDefinition> {

    public PollReifier(Route route, ProcessorDefinition<?> definition) {
        super(route, (PollDefinition) definition);
    }

    @Override
    public Processor createProcessor() throws Exception {
        String uri;
        if (definition.getEndpointConsumerBuilder() != null) {
            uri = definition.getEndpointConsumerBuilder().getRawUri();
        } else {
            uri = StringHelper.notEmpty(definition.getUri(), "uri", this);
        }
        Expression exp = createExpression(uri);
        long timeout = parseDuration(definition.getTimeout(), 20000);
        PollProcessor answer = new PollProcessor(exp, uri, timeout);
        answer.setVariableReceive(parseString(definition.getVariableReceive()));
        return answer;
    }

    protected Expression createExpression(String uri) {
        // make sure to parse property placeholders
        uri = EndpointHelper.resolveEndpointUriPropertyPlaceholders(camelContext, uri);

        // we use simple/constant language by default, but you can configure a different language
        String language = null;
        if (uri.startsWith("language:")) {
            String value = StringHelper.after(uri, "language:");
            language = StringHelper.before(value, ":");
            uri = StringHelper.after(value, ":");
        }
        if (language == null) {
            // only use simple language if needed
            language = LanguageSupport.hasSimpleFunction(uri) ? "simple" : "constant";
        }
        Language lan = camelContext.resolveLanguage(language);
        return lan.createExpression(uri);
    }

}
