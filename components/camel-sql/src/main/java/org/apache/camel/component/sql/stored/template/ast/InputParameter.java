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

import org.apache.camel.Expression;
import org.apache.camel.builder.ExpressionBuilder;

public class InputParameter {

    private final String name;
    private final int sqlType;
    private final Expression valueExpression;
    private final Class javaType;

    public InputParameter(String name, int sqlType, String valueSrcAsStr, Class javaType) {
        this.name = name;
        this.sqlType = sqlType;
        this.javaType = javaType;
        this.valueExpression = parseValueExpression(valueSrcAsStr);
    }

    private Expression parseValueExpression(String str) {
        return ExpressionBuilder.simpleExpression(str);
    }

    public String getName() {
        return name;
    }

    public int getSqlType() {
        return sqlType;
    }

    public Expression getValueExpression() {
        return valueExpression;
    }

    public Class getJavaType() {
        return javaType;
    }

}
