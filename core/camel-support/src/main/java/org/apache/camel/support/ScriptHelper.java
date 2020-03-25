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
package org.apache.camel.support;

import java.io.IOException;
import java.io.InputStream;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Language;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;

public final class ScriptHelper {

    private ScriptHelper() {
    }

    /**
     * Resolves the expression/predicate whether it refers to an external script on the file/classpath etc.
     * This requires to use the prefix <tt>resource:</tt> such as <tt>resource:classpath:com/foo/myscript.groovy</tt>,
     * <tt>resource:file:/var/myscript.groovy</tt>.
     * <p/>
     * If not then the returned value is returned as-is.
     */
    public static String resolveOptionalExternalScript(CamelContext camelContext, String expression) {
        return resolveOptionalExternalScript(camelContext, null, expression);
    }

    /**
     * Resolves the expression/predicate whether it refers to an external script on the file/classpath etc.
     * This requires to use the prefix <tt>resource:</tt> such as <tt>resource:classpath:com/foo/myscript.groovy</tt>,
     * <tt>resource:file:/var/myscript.groovy</tt>.
     * <p/>
     * If not then the returned value is returned as-is.
     * <p/>
     * If the exchange is provided (not null), then the external script can be referred via simple language for dynamic values, etc.
     * <tt>resource:classpath:${header.myFileName}</tt>
     */
    public static String resolveOptionalExternalScript(CamelContext camelContext, Exchange exchange, String expression) {
        if (expression == null) {
            return null;
        }
        String external = expression;

        // must be one line only
        int newLines = StringHelper.countChar(expression, '\n');
        if (newLines > 1) {
            // okay then just use as-is
            return expression;
        }

        // must start with resource: to denote an external resource
        if (external.startsWith("resource:")) {
            external = external.substring(9);

            if (ResourceHelper.hasScheme(external)) {
                if (exchange != null && LanguageSupport.hasSimpleFunction(external)) {
                    Language simple = exchange.getContext().resolveLanguage("simple");
                    external = simple.createExpression(external).evaluate(exchange, String.class);
                }

                InputStream is = null;
                try {
                    is = ResourceHelper.resolveMandatoryResourceAsInputStream(camelContext, external);
                    expression = camelContext.getTypeConverter().convertTo(String.class, is);
                } catch (IOException e) {
                    throw new RuntimeCamelException("Cannot load resource " + external, e);
                } finally {
                    IOHelper.close(is);
                }
            }
        }

        return expression;
    }

    public static boolean hasExternalScript(String external) {
        if (external.startsWith("resource:")) {
            external = external.substring(9);
            if (ResourceHelper.hasScheme(external) && LanguageSupport.hasSimpleFunction(external)) {
                return true;
            }
        }
        return false;
    }
}
