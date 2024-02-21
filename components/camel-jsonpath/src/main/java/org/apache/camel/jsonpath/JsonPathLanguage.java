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
import org.apache.camel.support.ExpressionAdapter;
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
    public Predicate createPredicate(Expression source, String expression, Object[] properties) {
        return doCreateJsonPathExpression(source, expression, properties, true);
    }

    @Override
    public Expression createExpression(Expression source, String expression, Object[] properties) {
        return doCreateJsonPathExpression(source, expression, properties, false);
    }

    protected ExpressionAdapter doCreateJsonPathExpression(
            Expression source, String expression, Object[] properties, boolean predicate) {
        JsonPathExpression answer = new JsonPathExpression(expression);
        answer.setSource(source);
        answer.setPredicate(predicate);
        answer.setResultType(property(Class.class, properties, 0, null));
        answer.setSuppressExceptions(property(boolean.class, properties, 2, isSuppressExceptions()));
        answer.setAllowSimple(property(boolean.class, properties, 3, isAllowSimple()));
        answer.setAllowEasyPredicate(property(boolean.class, properties, 4, isAllowEasyPredicate()));
        answer.setWriteAsString(property(boolean.class, properties, 5, isWriteAsString()));
        answer.setUnpackArray(property(boolean.class, properties, 6, isUnpackArray()));
        Object option = property(Object.class, properties, 7, null);
        if (option != null) {
            List<Option> list = new ArrayList<>();
            if (option instanceof String str) {
                for (String s : str.split(",")) {
                    list.add(getCamelContext().getTypeConverter().convertTo(Option.class, s));
                }
            } else if (option instanceof Option opt) {
                list.add(opt);
            }
            answer.setOptions(list.toArray(new Option[0]));
        } else if (options != null) {
            answer.setOptions(options);
        }
        if (getCamelContext() != null) {
            answer.init(getCamelContext());
        }
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
