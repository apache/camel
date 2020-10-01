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
package org.apache.camel.jsonpath;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.Option;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.spi.annotations.Language;
import org.apache.camel.support.LanguageSupport;

@Language("jsonpath")
public class JsonPathLanguage extends LanguageSupport {

    private Class<?> resultType;
    private boolean suppressExceptions;
    private boolean allowSimple = true;
    private boolean allowEasyPredicate = true;
    private boolean writeAsString;
    private String headerName;
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

    public boolean isAllowSimple() {
        return allowSimple;
    }

    public void setAllowSimple(boolean allowSimple) {
        this.allowSimple = allowSimple;
    }

    public boolean isAllowEasyPredicate() {
        return allowEasyPredicate;
    }

    public void setAllowEasyPredicate(boolean allowEasyPredicate) {
        this.allowEasyPredicate = allowEasyPredicate;
    }

    public boolean isWriteAsString() {
        return writeAsString;
    }

    public void setWriteAsString(boolean writeAsString) {
        this.writeAsString = writeAsString;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public Option[] getOptions() {
        return options;
    }

    public void setOptions(Option... options) {
        this.options = options;
    }

    @Override
    public Predicate createPredicate(String expression) {
        JsonPathExpression answer = (JsonPathExpression) createExpression(expression);
        answer.setPredicate(true);
        return answer;
    }

    @Override
    public Expression createExpression(String expression) {
        JsonPathExpression answer = new JsonPathExpression(expression);
        answer.setResultType(resultType);
        answer.setSuppressExceptions(suppressExceptions);
        answer.setAllowSimple(allowSimple);
        answer.setAllowEasyPredicate(allowEasyPredicate);
        answer.setHeaderName(headerName);
        answer.setWriteAsString(writeAsString);
        answer.setHeaderName(headerName);
        answer.setOptions(options);
        answer.afterPropertiesConfigured(getCamelContext());
        return answer;
    }

    @Override
    public Predicate createPredicate(Map<String, Object> properties) {
        JsonPathExpression json = (JsonPathExpression) createExpression(properties);
        json.setPredicate(true);
        return json;
    }

    @Override
    public Expression createExpression(Map<String, Object> properties) {
        String exp = (String) properties.get("expression");
        JsonPathExpression answer = new JsonPathExpression(exp);
        answer.setResultType(property(Class.class, properties, "resultType", resultType));
        answer.setSuppressExceptions(property(boolean.class, properties, "suppressExceptions", suppressExceptions));
        answer.setAllowEasyPredicate(property(boolean.class, properties, "allowEasyPredicate", allowEasyPredicate));
        answer.setAllowSimple(property(boolean.class, properties, "allowSimple", allowSimple));
        answer.setWriteAsString(property(boolean.class, properties, "writeAsString", writeAsString));
        answer.setHeaderName(property(String.class, properties, "headerName", headerName));
        String option = (String) properties.get("option");
        if (option != null) {
            List<Option> list = new ArrayList<>();
            for (String s : option.split(",")) {
                list.add(getCamelContext().getTypeConverter().convertTo(Option.class, s));
            }
            answer.setOptions(list.toArray(new Option[list.size()]));
        }
        answer.afterPropertiesConfigured(getCamelContext());
        return answer;
    }

}
