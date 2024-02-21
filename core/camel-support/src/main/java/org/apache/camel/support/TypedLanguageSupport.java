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
package org.apache.camel.support;

import org.apache.camel.Expression;
import org.apache.camel.spi.Language;
import org.apache.camel.support.builder.ExpressionBuilder;

/**
 * Base class for {@link Language} implementations that support a result type.
 */
public abstract class TypedLanguageSupport extends LanguageSupport {

    @Override
    public Expression createExpression(String expression, Object[] properties) {
        Class<?> type = property(Class.class, properties, 0, null);
        if (type == null || type == Object.class) {
            return createExpression(expression);
        }
        return ExpressionBuilder.convertToExpression(createExpression(expression), type);
    }
}
