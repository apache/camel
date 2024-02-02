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
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.jsonpath.easypredicate.EasyPredicateParser;
import org.apache.camel.spi.annotations.Language;
import org.apache.camel.support.ExpressionToPredicateAdapter;
import org.apache.camel.support.SingleInputTypedLanguageSupport;

@Language("jsonpath")
public class JsonPathLanguage extends SingleInputTypedLanguageSupport {

    @Override
    public Predicate createPredicate(String expression) {
        return ExpressionToPredicateAdapter.toPredicate(createExpression(expression));
    }

    @Override
    public Expression createExpression(String expression) {
        return createExpression(expression, null);
    }

    @Override
    public Predicate createPredicate(String expression, Object[] properties) {
        return ExpressionToPredicateAdapter.toPredicate(doCreateJsonPathExpression(expression, properties, true));
    }

    @Override
    public Expression createExpression(String expression, Object[] properties) {
        return doCreateJsonPathExpression(expression, properties, false);
    }

    protected Expression doCreateJsonPathExpression(String expression, Object[] properties, boolean predicate) {
        JsonPathExpression answer = new JsonPathExpression(expression);
        answer.setPredicate(predicate);
        answer.setResultType(property(Class.class, properties, 0, getResultType()));
        answer.setSuppressExceptions(property(boolean.class, properties, 1, false));
        answer.setAllowSimple(property(boolean.class, properties, 2, true));
        answer.setAllowEasyPredicate(property(boolean.class, properties, 3, true));
        answer.setWriteAsString(property(boolean.class, properties, 4, false));
        answer.setUnpackArray(property(boolean.class, properties, 5, false));
        answer.setHeaderName(property(String.class, properties, 6, getHeaderName()));
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
        }
        answer.setPropertyName(property(String.class, properties, 8, getPropertyName()));
        answer.setVariableName(property(String.class, properties, 9, getVariableName()));
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

}
