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
package org.apache.camel.builder;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.model.ProcessorType;
import org.apache.camel.spi.Language;

/**
 * Represents an expression clause within the DSL
 *
 * @version $Revision: 1.1 $
 */
public class ExpressionClause<T extends ProcessorType> {
    private T result;
    private CamelContext camelContext;

    /**
     * Evaluates the  <a href="http://activemq.apache.org/camel/el.html">EL Language from JSP and JSF</a>
     * using the <a href="http://activemq.apache.org/camel/juel.html">JUEL library</a>
     *
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T el(String text) {
        return language("el", text);
    }

    /**
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T groovy(String text) {
        return language("groovy", text);
    }

    /**
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T javaScript(String text) {
        return language("js", text);
    }

    /**
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T ognl(String text) {
        return language("ognl", text);
    }

    /**
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T php(String text) {
        return language("php", text);
    }

    /**
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T python(String text) {
        return language("python", text);
    }

    /**
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T ruby(String text) {
        return language("ruby", text);
    }

    /**
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T sql(String text) {
        return language("sql", text);
    }

    /**
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T simple(String text) {
        return language("simple", text);
    }

    /**
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T xpath(String text) {
        return language("xpath", text);
    }

    /**
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T xqery(String text) {
        return language("xqery", text);
    }

    /**
     * Evaluates a given language name with the expression text
     *
     * @param languageName the name of the language
     * @param text         the expression in the given language
     * @return the builder to continue processing the DSL
     */
    public T language(String languageName, String text) {
        Expression expression = createExpression("el", text);

        // TODO set the exception!
        return result;
    }

    protected Expression createExpression(String languageName, String text) {
        // TODO can we share this code with other places we assert mandatory language names?
        Language language = camelContext.resolveLanguage(languageName);
        if (language == null) {
            throw new IllegalArgumentException("Could not resolve language: " + languageName);
        }
        return language.createExpression(text);
    }
}
