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
package org.apache.camel.support;

import java.io.InputStream;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.ExpressionIllegalSyntaxException;
import org.apache.camel.IsSingleton;
import org.apache.camel.spi.Language;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ResourceHelper;

/**
 * Base language for {@link Language} implementations.
 */
public abstract class LanguageSupport implements Language, IsSingleton, CamelContextAware {

    public static final String RESOURCE = "resource:";

    private CamelContext camelContext;

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    /**
     * Loads the resource if the given expression is referring to an external resource by using
     * the syntax <tt>resource:scheme:uri<tt>.
     * If the expression is not referring to a resource, then its returned as is.
     * <p/>
     * For example <tt>resource:classpath:mygroovy.groovy</tt> to refer to a groovy script on the classpath.
     *
     * @param expression the expression
     * @return the expression
     * @throws ExpressionIllegalSyntaxException is thrown if error loading the resource
     */
    protected String loadResource(String expression) throws ExpressionIllegalSyntaxException {
        if (camelContext != null && expression.startsWith(RESOURCE)) {
            String uri = expression.substring(RESOURCE.length());
            InputStream is = null;
            try {
                is = ResourceHelper.resolveMandatoryResourceAsInputStream(camelContext, uri);
                expression = camelContext.getTypeConverter().mandatoryConvertTo(String.class, is);
            } catch (Exception e) {
                throw new ExpressionIllegalSyntaxException(expression, e);
            } finally {
                IOHelper.close(is);
            }
        }
        return expression;
    }
}
