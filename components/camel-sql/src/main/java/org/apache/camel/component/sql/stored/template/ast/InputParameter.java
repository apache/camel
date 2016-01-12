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
package org.apache.camel.component.sql.stored.template.ast;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.component.sql.stored.template.generated.SSPTParserConstants;
import org.apache.camel.component.sql.stored.template.generated.Token;

public class InputParameter {

    private final String name;
    private final int sqlType;
    private final Class javaType;
    private ValueExtractor valueExtractor;

    public InputParameter(String name, int sqlType, Token valueSrcToken, Class javaType) {
        this.name = name;
        this.sqlType = sqlType;
        this.javaType = javaType;
        parseValueExpression(valueSrcToken);
    }

    private void parseValueExpression(Token valueSrcToken) {
        if (SSPTParserConstants.SIMPLE_EXP_TOKEN == valueSrcToken.kind) {
            final Expression exp = ExpressionBuilder.simpleExpression(valueSrcToken.toString());
            this.valueExtractor = new ValueExtractor() {

                @Override
                public Object eval(Exchange exchange, Object container) {
                    return exp.evaluate(exchange, javaType);
                }
            };
        } else if (SSPTParserConstants.PARAMETER_POS_TOKEN == valueSrcToken.kind) {

            //remove leading :#
            final String mapKey = valueSrcToken.toString().substring(2);
            this.valueExtractor = new ValueExtractor() {
                @Override
                public Object eval(Exchange exchange, Object container) {
                    return ((Map) container).get(mapKey);
                }
            };
        }
    }

    /*public Object getParameterValueFromContainer(Exchange exchange, Object container) {
        if (this.valueExpression != null) {
            return valueExpression.evaluate(exchange, this.getJavaType());
        } else {
            return getValueFromMap((Map<String, Object>) container);
        }
    }*/

    /*private Object getValueFromMap(Map<String, Object> container) {
        return container.get(mapKey);
    }*/

    public String getName() {
        return name;
    }

    public int getSqlType() {
        return sqlType;
    }


    public Class getJavaType() {
        return javaType;
    }

    public ValueExtractor getValueExtractor() {
        return valueExtractor;
    }
}
