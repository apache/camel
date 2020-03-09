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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Message;
import org.apache.camel.TypeConverter;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.builder.ExpressionBuilder;

/**
 * Represents the strategy used to figure out how to map a message exchange to a POJO method invocation
 */
public final class DefaultParameterMappingStrategy implements ParameterMappingStrategy {

    public static final DefaultParameterMappingStrategy INSTANCE = new DefaultParameterMappingStrategy();

    private static final Map<Class<?>, Expression> MAP = new HashMap<>(6);

    static {
        MAP.put(Exchange.class, ExpressionBuilder.exchangeExpression());
        MAP.put(Message.class, ExpressionBuilder.inMessageExpression());
        MAP.put(Exception.class, ExpressionBuilder.exchangeExceptionExpression());
        MAP.put(TypeConverter.class, ExpressionBuilder.typeConverterExpression());
        MAP.put(Registry.class, ExpressionBuilder.registryExpression());
        MAP.put(CamelContext.class, ExpressionBuilder.camelContextExpression());
    };

    private DefaultParameterMappingStrategy() {
    }

    @Override
    public Expression getDefaultParameterTypeExpression(Class<?> parameterType) {
        return MAP.get(parameterType);
    }

}
