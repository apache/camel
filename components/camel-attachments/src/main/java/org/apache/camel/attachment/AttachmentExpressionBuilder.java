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
package org.apache.camel.attachment;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.language.simple.SimpleExpressionBuilder;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.camel.support.ObjectHelper;

import static org.apache.camel.support.builder.ExpressionBuilder.simpleExpression;

public class AttachmentExpressionBuilder {

    private static AttachmentMessage toAttachmentMessage(Exchange exchange) {
        AttachmentMessage answer;
        if (exchange.getMessage() instanceof AttachmentMessage am) {
            answer = am;
        } else {
            answer = new DefaultAttachmentMessage(exchange.getMessage());
        }
        return answer;
    }

    public static Expression attachments() {
        return new ExpressionAdapter() {
            @Override
            public Object evaluate(Exchange exchange) {
                return toAttachmentMessage(exchange).getAttachments();
            }
        };
    }

    public static Expression attachmentsSize() {
        return new ExpressionAdapter() {
            @Override
            public Object evaluate(Exchange exchange) {
                return toAttachmentMessage(exchange).getAttachments().size();
            }
        };
    }

    public static Expression attachmentContent(final String key, final String type) {
        return new ExpressionAdapter() {
            private Class<?> clazz;

            @Override
            public Object evaluate(Exchange exchange) {
                Object answer;
                var dh = toAttachmentMessage(exchange).getAttachment(key);
                try {
                    answer = dh.getContent();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                if (answer != null && clazz != null) {
                    try {
                        answer = exchange.getContext().getTypeConverter().mandatoryConvertTo(clazz, answer);
                    } catch (NoTypeConversionAvailableException e) {
                        throw CamelExecutionException.wrapCamelExecutionException(exchange, e);
                    }
                }
                return answer;
            }

            @Override
            public void init(CamelContext context) {
                if (type != null) {
                    try {
                        clazz = context.getClassResolver().resolveMandatoryClass(type);
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        };
    }

    public static Expression attachmentContentHeader(final String key, final String name, final String type) {
        return new ExpressionAdapter() {
            private Class<?> clazz;

            @Override
            public Object evaluate(Exchange exchange) {
                Object answer = null;
                var ao = toAttachmentMessage(exchange).getAttachmentObject(key);
                if (ao != null) {
                    answer = ao.getHeader(name);
                    if (answer != null && clazz != null) {
                        try {
                            answer = exchange.getContext().getTypeConverter().mandatoryConvertTo(clazz, answer);
                        } catch (NoTypeConversionAvailableException e) {
                            throw CamelExecutionException.wrapCamelExecutionException(exchange, e);
                        }
                    }
                }
                return answer;
            }

            @Override
            public void init(CamelContext context) {
                if (type != null) {
                    try {
                        clazz = context.getClassResolver().resolveMandatoryClass(type);
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        };
    }

    public static Expression attachmentContentType(final String key) {
        return new ExpressionAdapter() {
            @Override
            public Object evaluate(Exchange exchange) {
                var dh = toAttachmentMessage(exchange).getAttachment(key);
                if (dh != null) {
                    return dh.getContentType();
                }
                return null;
            }
        };
    }

    /**
     * Returns an expression for the attachment value of exchange with the given name
     *
     * @param  attachmentName the name of the attachment the expression will return
     * @return                an expression object which will return the property value
     */
    public static Expression attachmentExpression(final String attachmentName) {
        return attachmentExpression(simpleExpression(attachmentName), false);
    }

    /**
     * Returns an expression for the attachment value of exchange with the given name
     *
     * @param  attachmentName the name of the attachment the expression will return
     * @param  mandatory      whether the attachment is mandatory and if not present an exception is thrown
     * @return                an expression object which will return the attachment value
     */
    public static Expression attachmentExpression(final Expression attachmentName, final boolean mandatory) {
        return new ExpressionAdapter() {
            @Override
            public Object evaluate(Exchange exchange) {
                String key = attachmentName.evaluate(exchange, String.class);
                Object answer = toAttachmentMessage(exchange).getAttachment(key);
                if (mandatory && answer == null) {
                    throw RuntimeCamelException.wrapRuntimeCamelException(new NoSuchAttachmentException(exchange, key));
                }
                return answer;
            }

            @Override
            public void init(CamelContext context) {
                super.init(context);
                attachmentName.init(context);
            }

            @Override
            public String toString() {
                return "attachment(" + attachmentName + ")";
            }
        };
    }

    /**
     * Returns an expression for the attachment value of exchange with the given name invoking methods defined in a
     * simple OGNL notation
     *
     * @param ognl methods to invoke on the attachment in a simple OGNL syntax
     */
    public static Expression attachmentOgnlExpression(final String ognl) {
        return new SimpleExpressionBuilder.KeyedOgnlExpressionAdapter(
                ognl, "attachmentOgnl(" + ognl + ")",
                (exchange, exp) -> {
                    String text = exp.evaluate(exchange, String.class);
                    var am = toAttachmentMessage(exchange);
                    var dh = am.getAttachment(text);
                    if (dh == null && ObjectHelper.isNumber(text)) {
                        try {
                            // fallback to lookup by numeric index
                            int idx = Integer.parseInt(text);
                            if (idx < am.getAttachments().size()) {
                                var it = am.getAttachments().values().iterator();
                                for (int i = 0; i < idx; i++) {
                                    it.next();
                                }
                                dh = it.next();
                            }
                        } catch (NumberFormatException e) {
                            // ignore
                        }
                    }
                    return dh;
                });
    }

}
