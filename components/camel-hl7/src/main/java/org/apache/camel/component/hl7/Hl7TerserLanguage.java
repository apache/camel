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
package org.apache.camel.component.hl7;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.util.Terser;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.camel.support.SingleInputTypedLanguageSupport;
import org.apache.camel.support.builder.ExpressionBuilder;
import org.apache.camel.util.ObjectHelper;

@org.apache.camel.spi.annotations.Language("hl7terser")
public class Hl7TerserLanguage extends SingleInputTypedLanguageSupport {

    public static Expression terser(final String expression) {
        return terser(ExpressionBuilder.bodyExpression(), expression);
    }

    public static Expression terser(final Expression source, final String expression) {
        ObjectHelper.notNull(expression, "expression");
        return new ExpressionAdapter() {

            @Override
            public Object evaluate(Exchange exchange) {
                Message message = source.evaluate(exchange, Message.class);
                try {
                    return new Terser(message).get(expression.trim());
                } catch (HL7Exception e) {
                    throw RuntimeCamelException.wrapRuntimeCamelException(e);
                }
            }

            @Override
            public void init(CamelContext context) {
                source.init(context);
            }

            @Override
            public String toString() {
                return "hl7terser(" + expression + ")";
            }

        };
    }

    @Override
    public Expression createExpression(Expression source, String expression, Object[] properties) {
        return terser(source, expression);
    }
}
