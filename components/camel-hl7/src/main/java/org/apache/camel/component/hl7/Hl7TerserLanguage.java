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

import java.io.File;
import java.io.InputStream;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.util.Terser;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.ExpressionEvaluationException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.StreamCache;
import org.apache.camel.WrappedFile;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.annotations.Language;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.camel.support.SingleInputTypedLanguageSupport;
import org.apache.camel.support.builder.ExpressionBuilder;
import org.apache.camel.util.ObjectHelper;

@Language("hl7terser")
@BodyTypes(expressionIn = @BodyType(value = { Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE, Carrier.POJO },
                                    format = "hl7", dependsOn = "source",
                                    description = "An HL7 message as text or bytes, in ER7 or XML, a stream or a file of it, read"
                                                  + " in the charset of MSH-18, a HAPI Message, or a DOM of HL7 XML. Another"
                                                  + " body fails"),
           expressionOut = @BodyType(value = { Carrier.TEXT, Carrier.NULL }, javaType = "java.lang.String",
                                     dependsOn = "resultType",
                                     description = "The value of the field as text, null when the field is empty"))
public class Hl7TerserLanguage extends SingleInputTypedLanguageSupport {

    public static Expression terser(final String expression) {
        return terser(ExpressionBuilder.bodyExpression(), expression);
    }

    public static Expression terser(final Expression source, final String expression) {
        ObjectHelper.notNull(expression, "expression");
        return new ExpressionAdapter() {

            @Override
            public Object evaluate(Exchange exchange) {
                Message message = toMessage(exchange, source.evaluate(exchange, Object.class));
                try {
                    return new Terser(message).get(expression.trim());
                } catch (HL7Exception e) {
                    throw RuntimeCamelException.wrapRuntimeCamelException(e);
                }
            }

            private Message toMessage(Exchange exchange, Object body) {
                if (body instanceof Message message) {
                    return message;
                }
                Message answer = null;
                if (body != null) {
                    try {
                        if (body instanceof InputStream || body instanceof StreamCache || body instanceof WrappedFile
                                || body instanceof File) {
                            // read a stream or a file as text, in the charset of MSH-18 (like the HL7 data format)
                            byte[] bytes = exchange.getContext().getTypeConverter().mandatoryConvertTo(byte[].class, exchange,
                                    body);
                            String charsetName = HL7Charset.getCharsetName(bytes, ExchangeHelper.getCharsetName(exchange));
                            body = new String(bytes, charsetName);
                        }
                        answer = exchange.getContext().getTypeConverter().convertTo(Message.class, exchange, body);
                    } catch (Exception e) {
                        throw new ExpressionEvaluationException(
                                this, "Cannot evaluate " + this + " as " + source + " is not a valid HL7 message", exchange, e);
                    }
                }
                if (answer == null) {
                    String type = body != null ? "of type " + body.getClass().getName() : "null";
                    throw new ExpressionEvaluationException(
                            this, "Cannot evaluate " + this + " as " + source + " is " + type
                                  + ". Accepted are text or bytes of an HL7 message in ER7 or XML (also from a stream or"
                                  + " a file), a HAPI Message, or a DOM of HL7 XML",
                            exchange, null);
                }
                return answer;
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
