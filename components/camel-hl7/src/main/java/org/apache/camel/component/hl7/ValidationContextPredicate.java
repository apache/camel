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
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.validation.MessageValidator;
import ca.uhn.hl7v2.validation.ValidationContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.ExpressionBuilder;

public class ValidationContextPredicate implements Predicate {

    private Expression validatorExpression;

    public ValidationContextPredicate() {
        this((Expression)null);
    }

    public ValidationContextPredicate(HapiContext hapiContext) {
        this(hapiContext.getValidationContext());
    }

    public ValidationContextPredicate(ValidationContext validationContext) {
        this(ExpressionBuilder.constantExpression(validationContext));
    }

    public ValidationContextPredicate(Expression expression) {
        this.validatorExpression = expression;
    }

    @Override
    public boolean matches(Exchange exchange) {
        try {
            Message message = exchange.getIn().getBody(Message.class);
            ValidationContext context = validatorExpression != null
                    ? validatorExpression.evaluate(exchange, ValidationContext.class)
                    : dynamicValidationContext(message, exchange.getIn().getHeader(HL7Constants.HL7_CONTEXT, HapiContext.class));
            MessageValidator validator = new MessageValidator(context, false);
            return validator.validate(message);
        } catch (HL7Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }

    private ValidationContext dynamicValidationContext(Message message, HapiContext hapiContext) {
        return hapiContext != null
                ? hapiContext.getValidationContext()
                : message.getParser().getHapiContext().getValidationContext();
    }
}
