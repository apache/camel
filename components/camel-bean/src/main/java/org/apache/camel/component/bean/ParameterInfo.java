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
package org.apache.camel.component.bean;

import java.lang.annotation.Annotation;
import java.util.Arrays;

import org.apache.camel.Expression;

/**
 * Parameter information to be used for method invocation.
 */
final class ParameterInfo {

    private final int index;
    private final Class<?> type;
    private final Annotation[] annotations;
    private Expression expression;

    ParameterInfo(int index, Class<?> type, Annotation[] annotations, Expression expression) {
        this.index = index;
        this.type = type;
        this.annotations = annotations;
        this.expression = expression;
    }

    public Annotation[] getAnnotations() {
        return annotations;
    }

    public Expression getExpression() {
        return expression;
    }

    public int getIndex() {
        return index;
    }

    public Class<?> getType() {
        return type;
    }

    public void setExpression(Expression expression) {
        this.expression = expression;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("ParameterInfo");
        sb.append("[index=").append(index);
        sb.append(", type=").append(type);
        sb.append(", annotations=").append(annotations == null ? "null" : Arrays.asList(annotations).toString());
        sb.append(", expression=").append(expression);
        sb.append(']');
        return sb.toString();
    }
}
