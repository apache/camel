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
import org.apache.velocity.app.Velocity;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.context.Context;
import org.apache.velocity.runtime.log.SimpleLog4JLogSystem;
import org.springframework.core.io.Resource;

/**
 * @version $Revision$
 */
public class VelocityEndpoint extends ResourceBasedEndpoint {
    private final VelocityComponent component;
    private VelocityEngine velocityEngine;
    private boolean loaderCache = true;

    public VelocityEndpoint(String uri, VelocityComponent component, String resourceUri, Map parameters) {
        super(uri, component, resourceUri, null);
        this.component = component;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public ExchangePattern getExchangePattern() {
        return ExchangePattern.InOut;
    }

    private VelocityEngine getVelocityEngine() throws Exception {
        if (velocityEngine == null) {
            velocityEngine = component.getVelocityEngine();
            velocityEngine.setProperty(Velocity.FILE_RESOURCE_LOADER_CACHE, isLoaderCache() ? Boolean.TRUE : Boolean.FALSE);
            velocityEngine.setProperty(Velocity.RUNTIME_LOG_LOGSYSTEM_CLASS, SimpleLog4JLogSystem.class.getName());
            velocityEngine.setProperty("runtime.log.logsystem.log4j.category", VelocityEndpoint.class.getName());
            velocityEngine.init();
        }
        return velocityEngine;
    }

    public void setVelocityEngine(VelocityEngine velocityEngine) {
        this.velocityEngine = velocityEngine;
    }

    public boolean isLoaderCache() {
        return loaderCache;
    }

    /**
     * Enables / disables the velocity resource loader cache which is enabled by default
     *
     * @param loaderCache a flag to enable/disable the cache
     */
    public void setLoaderCache(boolean loaderCache) {
        this.loaderCache = loaderCache;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void onExchange(Exchange exchange) throws Exception {
        Resource resource = getResource();

        // getResourceAsInputStream also considers the content cache
        Reader reader = new InputStreamReader(getResourceAsInputStream());
        StringWriter buffer = new StringWriter();
        String logTag = getClass().getName();
        Map variableMap = ExchangeHelper.createVariableMap(exchange);
        Context velocityContext = new VelocityContext(variableMap);

        // let velocity parse and generate the result in buffer
        VelocityEngine engine = getVelocityEngine();
        if (log.isDebugEnabled()) {
            log.debug("Velocity is evaluating using velocity context: " + variableMap);
        }
        engine.evaluate(velocityContext, buffer, logTag, reader);

        // now lets output the results to the exchange
        Message out = exchange.getOut(true);
        out.setBody(buffer.toString());
        out.setHeader("org.apache.camel.velocity.resource", resource);
        Map<String, Object> headers = (Map<String, Object>)velocityContext.get("headers");
        for (String key : headers.keySet()) {
            out.setHeader(key, headers.get(key));
        }
    }

}
