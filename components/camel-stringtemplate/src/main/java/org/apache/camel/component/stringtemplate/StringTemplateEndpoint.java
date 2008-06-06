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
package org.apache.camel.component.stringtemplate;

import java.io.StringWriter;
import java.util.Map;

import org.antlr.stringtemplate.AutoIndentWriter;
import org.antlr.stringtemplate.StringTemplate;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.ResourceBasedEndpoint;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.util.ExchangeHelper;

/**
 * @version $Revision$
 */
public class StringTemplateEndpoint extends ResourceBasedEndpoint {

    public StringTemplateEndpoint(String uri, StringTemplateComponent component, String resourceUri, Map parameters) {
        super(uri, component, resourceUri, null);
    }

    public StringTemplateEndpoint(String endpointUri, Processor processor, String resourceUri) {
        super(endpointUri, processor, resourceUri);
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public ExchangePattern getExchangePattern() {
        return ExchangePattern.InOut;
    }

    @Override
    protected void onExchange(Exchange exchange) throws Exception {
        StringWriter buffer = new StringWriter();
        Map variableMap = ExchangeHelper.createVariableMap(exchange);

        // getResourceAsInputStream also considers the content cache
        String text = IOConverter.toString(getResourceAsInputStream());
        StringTemplate template = new StringTemplate(text);
        template.setAttributes(variableMap);
        if (log.isDebugEnabled()) {
            log.debug("StringTemplate is writing using attributes: " + variableMap);
        }
        template.write(new AutoIndentWriter(buffer));

        // now lets output the results to the exchange
        Message out = exchange.getOut(true);
        out.setBody(buffer.toString());
        out.setHeader("org.apache.camel.stringtemplate.resource", getResource());
    }
    
}