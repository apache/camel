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
package org.apache.camel.jsonpath;

import com.jayway.jsonpath.Option;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.support.LanguageSupport;

public class JsonPathLanguage extends LanguageSupport {

    private Class<?> resultType;
    private boolean suppressExceptions;
    private Option[] options;

    public Class<?> getResultType() {
        return resultType;
    }

    public void setResultType(Class<?> resultType) {
        this.resultType = resultType;
    }

    public boolean isSuppressExceptions() {
        return suppressExceptions;
    }

    public void setSuppressExceptions(boolean suppressExceptions) {
        this.suppressExceptions = suppressExceptions;
    }

    public Option[] getOptions() {
        return options;
    }

    public void setOption(Option option) {
        this.options = new Option[]{option};
    }

    public void setOptions(Option[] options) {
        this.options = options;
    }

    @Override
    public Predicate createPredicate(final String predicate) {
        JsonPathExpression answer = new JsonPathExpression(predicate);
        answer.setPredicate(true);
        answer.setResultType(resultType);
        answer.setSuppressExceptions(suppressExceptions);
        answer.setOptions(options);
        answer.afterPropertiesConfigured(getCamelContext());
        return answer;
    }

    @Override
    public Expression createExpression(final String expression) {
        JsonPathExpression answer = new JsonPathExpression(expression);
        answer.setPredicate(false);
        answer.setResultType(resultType);
        answer.setSuppressExceptions(suppressExceptions);
        answer.setOptions(options);
        answer.afterPropertiesConfigured(getCamelContext());
        return answer;
    }

    @Override
    public boolean isSingleton() {
        // cannot be singleton due options
        return false;
    }
}
