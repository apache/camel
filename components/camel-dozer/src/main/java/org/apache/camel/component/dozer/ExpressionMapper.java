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
package org.apache.camel.component.dozer;

import java.io.IOException;
import java.io.InputStream;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.spi.Language;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ResourceHelper;

/**
 * Provides support for mapping a Camel expression to a target field in a 
 * mapping.  Expressions have the following format:
 * <br><br>
 * [language]:[expression]
 * <br><br>
 */
public class ExpressionMapper extends BaseConverter {
    
    private ThreadLocal<Exchange> currentExchange = new ThreadLocal<Exchange>();
    
    @Override
    public Object convert(Object existingDestinationFieldValue, 
            Object sourceFieldValue, 
            Class<?> destinationClass,
            Class<?> sourceClass) {
        try {
            if (currentExchange.get() == null) {
                throw new IllegalStateException(
                        "Current exchange has not been set for ExpressionMapper");
            }

            Expression exp;

            // Resolve the language being used for this expression and evaluate
            Exchange exchange = currentExchange.get();
            Language expLang = exchange.getContext().resolveLanguage(getLanguagePart());
            String scheme = getSchemePart();
            if (scheme != null && (scheme.equalsIgnoreCase("classpath") || scheme.equalsIgnoreCase("file") || scheme.equalsIgnoreCase("http"))) {
                String path = getPathPart();
                try {
                    exp = expLang.createExpression(resolveScript(scheme + ":" + path));
                } catch (IOException e) {
                    throw new IllegalStateException(
                            "Expression script specified but not found", e);
                }
            } else {
                exp = expLang.createExpression(getExpressionPart());
            }
            return exp.evaluate(exchange, destinationClass);
        } finally {
            done();
        }
    }
    
    /**
     * Resolves the script.
     *
     * @param script script or uri for a script to load
     * @return the script
     * @throws IOException is thrown if error loading the script
     */
    protected String resolveScript(String script) throws IOException {
        String answer;
        if (ResourceHelper.hasScheme(script)) {
            InputStream is = loadResource(script);
            answer = currentExchange.get().getContext().getTypeConverter().convertTo(String.class, is);
            IOHelper.close(is);
        } else {
            answer = script;
        }

        return answer;
    }
    
    /**
     * Loads the given resource.
     *
     * @param uri uri of the resource.
     * @return the loaded resource
     * @throws IOException is thrown if resource is not found or cannot be loaded
     */
    protected InputStream loadResource(String uri) throws IOException {
        return ResourceHelper.resolveMandatoryResourceAsInputStream(currentExchange.get().getContext(), uri);
    }
    
    /**
     * Used as the source field for Dozer mappings. 
     */
    public String getExpression() {
        return getParameter();
    }
    
    /**
     * The actual expression, without the language prefix.
     */
    public String getExpressionPart() {
        return getParameter().substring(getParameter().indexOf(":") + 1);
    }
    
    /**
     * The expression language used for this mapping.
     */
    public String getLanguagePart() {
        return getParameter().substring(0, getParameter().indexOf(":"));
    }
    
    /**
     * Sets the Camel exchange reference for this mapping.  The exchange 
     * reference is stored in a thread-local which is cleaned up after the 
     * mapping has been performed via the done() method.
     * @param exchange
     */
    public void setCurrentExchange(Exchange exchange) {
        currentExchange.set(exchange);
    }
    
    /**
     * The scheme used for this mapping's resource file (classpath, file, http).
     */
    public String getSchemePart() {
        return getParameterPart(":", 1);
    }

    /**
     * The path used for this mapping's resource file.
     */
    public String getPathPart() {
        return getParameterPart(":", 2);
    }

    /*
     * Parse the URI to get at one of the parameters.
     * @param separator
     * @param idx
     * @return
     */
    private String getParameterPart(String separator, int idx) {
        String part = null;
        String[] parts = getParameter().split(separator);
        if (parts.length > idx) {
            part = parts[idx];
        }
        return part;
    }
    
}
