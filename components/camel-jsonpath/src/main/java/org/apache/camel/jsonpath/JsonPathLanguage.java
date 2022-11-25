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
import org.apache.camel.support.SingleInputTypedLanguageSupport;
import org.apache.camel.support.component.PropertyConfigurerSupport;

@Language("jsonpath")
public class JsonPathLanguage extends SingleInputTypedLanguageSupport implements PropertyConfigurer {

    private boolean suppressExceptions;
    private boolean allowSimple = true;
    private boolean allowEasyPredicate = true;
    private boolean writeAsString;
    private boolean unpackArray;
    private Option[] options;

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

    public boolean isUnpackArray() {
        return unpackArray;
    }

    public void setUnpackArray(boolean unpackArray) {
        this.unpackArray = unpackArray;
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
        answer.setResultType(getResultType());
        answer.setSuppressExceptions(suppressExceptions);
        answer.setAllowSimple(allowSimple);
        answer.setAllowEasyPredicate(allowEasyPredicate);
        answer.setHeaderName(getHeaderName());
        answer.setWriteAsString(writeAsString);
        answer.setUnpackArray(unpackArray);
        answer.setPropertyName(getPropertyName());
        answer.setOptions(options);
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
        answer.setResultType(property(Class.class, properties, 0, getResultType()));
        answer.setSuppressExceptions(property(boolean.class, properties, 1, suppressExceptions));
        answer.setAllowSimple(property(boolean.class, properties, 2, allowSimple));
        answer.setAllowEasyPredicate(property(boolean.class, properties, 3, allowEasyPredicate));
        answer.setWriteAsString(property(boolean.class, properties, 4, writeAsString));
        answer.setUnpackArray(property(boolean.class, properties, 5, unpackArray));
        answer.setHeaderName(property(String.class, properties, 6, getHeaderName()));
        String option = (String) properties[7];
        if (option != null) {
            List<Option> list = new ArrayList<>();
            for (String s : option.split(",")) {
                list.add(getCamelContext().getTypeConverter().convertTo(Option.class, s));
            }
            answer.setOptions(list.toArray(new Option[0]));
        }
        answer.setPropertyName(property(String.class, properties, 8, getPropertyName()));
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
            case "propertyname":
            case "propertyName":
                setPropertyName(PropertyConfigurerSupport.property(camelContext, String.class, value));
                return true;
            case "writeasstring":
            case "writeAsString":
                setWriteAsString(PropertyConfigurerSupport.property(camelContext, boolean.class, value));
                return true;
            case "unpackarray":
            case "unpackArray":
                setUnpackArray(PropertyConfigurerSupport.property(camelContext, boolean.class, value));
                return true;
            case "options":
                setOptions(PropertyConfigurerSupport.property(camelContext, Option[].class, value));
                return true;
            default:
                return false;
        }
    }
}
