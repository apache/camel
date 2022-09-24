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

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.jsonpath.easypredicate.EasyPredicateParser;
import org.apache.camel.spi.PropertyConfigurer;
import org.apache.camel.spi.annotations.Language;
import org.apache.camel.support.LanguageSupport;
import org.apache.camel.support.component.PropertyConfigurerSupport;

@Language("jsonpath")
public class JsonPathLanguage extends LanguageSupport implements PropertyConfigurer {

    private Class<?> resultType;
    private boolean suppressExceptions;
    private boolean allowSimple = true;
    private boolean allowEasyPredicate = true;
    private boolean supportPojoAsMapAndList = true;
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

    public boolean isSupportPojoAsMapAndList() {
        return supportPojoAsMapAndList;
    }

    public void setSupportPojoAsMapAndList(boolean supportPojoAsMapAndList) {
        this.supportPojoAsMapAndList = supportPojoAsMapAndList;
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
        answer.setOptions(options);
        answer.setSupportPojoAsMapAndList(supportPojoAsMapAndList);
        answer.init(getCamelContext());
        return answer;
    }

    @Override
    public Predicate createPredicate(String expression, Object[] properties) {
        JsonPathExpression json = (JsonPathExpression) createExpression(expression, properties);
        json.setPredicate(true);
        return json;
    }

    @Override
    public Expression createExpression(String expression, Object[] properties) {
        JsonPathExpression answer = new JsonPathExpression(expression);
        answer.setResultType(property(Class.class, properties, 0, resultType));
        answer.setSuppressExceptions(property(boolean.class, properties, 1, suppressExceptions));
        answer.setAllowSimple(property(boolean.class, properties, 2, allowSimple));
        answer.setAllowEasyPredicate(property(boolean.class, properties, 3, allowEasyPredicate));
        answer.setWriteAsString(property(boolean.class, properties, 4, writeAsString));
        answer.setHeaderName(property(String.class, properties, 5, headerName));
        String option = (String) properties[6];
        if (option != null) {
            List<Option> list = new ArrayList<>();
            for (String s : option.split(",")) {
                list.add(getCamelContext().getTypeConverter().convertTo(Option.class, s));
            }
            answer.setOptions(list.toArray(new Option[0]));
        }
        answer.setSupportPojoAsMapAndList(property(boolean.class, properties, 7, supportPojoAsMapAndList));
        answer.init(getCamelContext());
        return answer;
    }

    // use by tooling
    public boolean validateExpression(String expression) {
        JsonPath.compile(expression);
        return true;
    }

    // use by tooling
    public boolean validatePredicate(String expression) {
        EasyPredicateParser parser = new EasyPredicateParser();
        String exp = parser.parse(expression);
        JsonPath.compile(exp);
        return true;
    }

    @Override
    public boolean configure(CamelContext camelContext, Object target, String name, Object value, boolean ignoreCase) {
        if (target != this) {
            throw new IllegalStateException("Can only configure our own instance !");
        }

        switch (ignoreCase ? name.toLowerCase() : name) {
            case "resulttype":
            case "resultType":
                setResultType(PropertyConfigurerSupport.property(camelContext, Class.class, value));
                return true;
            case "suppressexceptions":
            case "suppressExceptions":
                setSuppressExceptions(PropertyConfigurerSupport.property(camelContext, boolean.class, value));
                return true;
            case "allowsimple":
            case "allowSimple":
                setAllowSimple(PropertyConfigurerSupport.property(camelContext, boolean.class, value));
                return true;
            case "alloweasypredicate":
            case "allowEasyPredicate":
                setAllowEasyPredicate(PropertyConfigurerSupport.property(camelContext, boolean.class, value));
                return true;
            case "headername":
            case "headerName":
                setHeaderName(PropertyConfigurerSupport.property(camelContext, String.class, value));
                return true;
            case "writeasstring":
            case "writeAsString":
                setWriteAsString(PropertyConfigurerSupport.property(camelContext, boolean.class, value));
                return true;
            case "supportpojoasmapandlist":
            case "supportPojoAsMapAndList":
                setSupportPojoAsMapAndList(PropertyConfigurerSupport.property(camelContext, boolean.class, value));
                return true;
            case "options":
                setOptions(PropertyConfigurerSupport.property(camelContext, Option[].class, value));
                return true;
            default:
                return false;
        }
    }
}
