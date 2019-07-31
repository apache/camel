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
package org.apache.camel.component.sql.stored.template.ast;

import java.util.Map;

import org.apache.camel.Expression;
import org.apache.camel.component.sql.stored.template.generated.SSPTParserConstants;
import org.apache.camel.component.sql.stored.template.generated.Token;

public class InOutParameter {

    private final String typeName;
    private final int sqlType;
    private final Integer scale;
    private ValueExtractor valueExtractor;
    private String outValueMapKey;

    public InOutParameter(int sqlType, Token valueSrcToken, Integer scale, String typeName, String outValueMapKey) {
        this.sqlType = sqlType;
        parseValueExpression(valueSrcToken);
        this.scale = scale;
        this.typeName = typeName;
        this.outValueMapKey = outValueMapKey;

        if (this.scale != null && this.typeName != null) {
            throw new ParseRuntimeException(String.format("Both scale=%s and typeName=%s cannot be set", this.scale, this.typeName));
        }
    }

    private void parseValueExpression(Token valueSrcToken) {
        if (SSPTParserConstants.SIMPLE_EXP_TOKEN == valueSrcToken.kind) {
            this.valueExtractor = (exchange, container) -> {
                Expression exp = exchange.getContext().resolveLanguage("simple").createExpression(valueSrcToken.toString());
                return exp.evaluate(exchange, Object.class);
            };
        } else if (SSPTParserConstants.PARAMETER_POS_TOKEN == valueSrcToken.kind) {
            //remove leading :#
            final String mapKey = valueSrcToken.toString().substring(2);
            this.valueExtractor = (exchange, container) -> ((Map) container).get(mapKey);
        }
    }

    public String getTypeName() {
        return typeName;
    }

    public int getSqlType() {
        return sqlType;
    }

    public Integer getScale() {
        return scale;
    }

    public ValueExtractor getValueExtractor() {
        return valueExtractor;
    }

    public String getOutValueMapKey() {
        return outValueMapKey;
    }
}
