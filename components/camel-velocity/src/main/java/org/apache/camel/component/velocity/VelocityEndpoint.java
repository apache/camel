/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.velocity;

import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.component.ResourceBasedEndpoint;
import org.apache.camel.util.ExchangeHelper;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.context.Context;

/**
 * @version $Revision: 1.1 $
 */
public class VelocityEndpoint extends ResourceBasedEndpoint {
    private final VelocityComponent component;
    private VelocityEngine velocityEngine;

    public VelocityEndpoint(String uri, VelocityComponent component, String resourceUri, Map parameters) {
        super(uri, component, resourceUri, null);
        this.component = component;
    }

    public boolean isSingleton() {
        return true;
    }

    @Override
    public ExchangePattern getExchangePattern() {
        return ExchangePattern.InOut;
    }

    public VelocityEngine getVelocityEngine() throws Exception {
        if (velocityEngine == null) {
            velocityEngine = component.getVelocityEngine();
            velocityEngine.init();
        }
        return velocityEngine;
    }

    public void setVelocityEngine(VelocityEngine velocityEngine) {
        this.velocityEngine = velocityEngine;
    }

    @Override
    protected void onExchange(Exchange exchange) throws Exception {
        // TODO we might wanna add some kinda resource caching of the template
        Reader reader = new InputStreamReader(getResource().getInputStream());
        StringWriter buffer = new StringWriter();
        String logTag = getClass().getName();
        Map variableMap = ExchangeHelper.createVariableMap(exchange);
        Context velocityContext = new VelocityContext(variableMap);
        VelocityEngine engine = getVelocityEngine();
        engine.evaluate(velocityContext, buffer, logTag, reader);

        // now lets output the results to the exchange
        Message out = exchange.getOut(true);
        out.setBody(buffer.toString());
        out.setHeader("org.apache.camel.velocity.resource", getResource());
    }
}
