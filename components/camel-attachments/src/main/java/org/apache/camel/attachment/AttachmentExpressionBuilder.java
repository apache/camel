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

import java.io.File;
import java.io.InputStream;
import java.util.Iterator;

import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.language.simple.SimpleExpressionBuilder;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.support.ResourceHelper;

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

    public static Expression attachmentsKeys() {
        return new ExpressionAdapter() {
            @Override
            public Object evaluate(Exchange exchange) {
                return toAttachmentMessage(exchange).getAttachmentNames();
            }
        };
    }

    public static Expression clearAttachments() {
        return new ExpressionAdapter() {
            @Override
            public Object evaluate(Exchange exchange) {
                toAttachmentMessage(exchange).clearAttachments();
                return null;
            }
        };
    }

    public static Expression attachmentContent(final String key, final String type) {
        return new ExpressionAdapter() {
            private Class<?> clazz;

            @Override
            public Object evaluate(Exchange exchange) {
                Object answer = null;
                var dh = lookupDataHandlerByKey(exchange, key);
                if (dh != null) {
                    try {
                        answer = dh.getContent();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
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
                var ao = lookupAttachmentObjectByKey(exchange, key);
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
                var dh = lookupDataHandlerByKey(exchange, key);
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
                Object answer = lookupAttachmentObjectByKey(exchange, key);
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
                    String key = exp.evaluate(exchange, String.class);
                    return lookupDataHandlerByKey(exchange, key);
                });
    }

    /**
     * Sets the attachment with the given expression value
     */
    public static Expression setAttachmentExpression(
            final String attachmentName, final String expression) {
        return new ExpressionAdapter() {
            private Expression exp;

            @Override
            public void init(CamelContext context) {
                exp = context.resolveLanguage("simple").createExpression(expression);
                exp.init(context);
            }

            @Override
            public Object evaluate(Exchange exchange) {
                try {
                    Object value = exp.evaluate(exchange, Object.class);
                    if (value != null) {
                        AttachmentMessage am = toAttachmentMessage(exchange);
                        DataSource ds;
                        if (value instanceof File f) {
                            ds = new CamelFileDataSource(f, attachmentName);
                        } else if (value instanceof String str) {
                            byte[] data;
                            if (ResourceHelper.hasScheme(str)) {
                                InputStream is
                                        = ResourceHelper.resolveMandatoryResourceAsInputStream(exchange.getContext(), str);
                                data = exchange.getContext().getTypeConverter().convertTo(byte[].class, is);
                            } else {
                                data = str.getBytes();
                            }
                            ds = new ByteArrayDataSource(attachmentName, data);
                        } else {
                            byte[] data = exchange.getContext().getTypeConverter().convertTo(byte[].class, value);
                            ds = new ByteArrayDataSource(attachmentName, data);
                        }
                        am.addAttachment(attachmentName, new DataHandler(ds));
                    } else {
                        AttachmentMessage am = toAttachmentMessage(exchange);
                        am.removeAttachment(attachmentName);
                    }
                } catch (Exception e) {
                    throw RuntimeCamelException.wrapRuntimeCamelException(e);
                }
                // does not return anything
                return null;
            }

            @Override
            public String toString() {
                return "setAttachment(" + attachmentName + "," + expression + ")";
            }
        };
    }

    private static DataHandler lookupDataHandlerByKey(Exchange exchange, String key) {
        AttachmentMessage am = toAttachmentMessage(exchange);
        var dh = am.getAttachment(key);
        if (dh == null && ObjectHelper.isNumber(key)) {
            Integer idx = exchange.getContext().getTypeConverter().tryConvertTo(Integer.class, key);
            if (idx != null) {
                Iterator<?> it = ObjectHelper.createIterator(am.getAttachments().keySet());
                for (int i = 0; i <= idx && it.hasNext(); i++) {
                    if (i == idx) {
                        key = it.next().toString();
                    } else {
                        key = null;
                        it.next();
                    }
                }
                if (key != null) {
                    dh = am.getAttachment(key);
                }
            }
        }
        return dh;
    }

    private static Attachment lookupAttachmentObjectByKey(Exchange exchange, String key) {
        AttachmentMessage am = toAttachmentMessage(exchange);
        var ao = am.getAttachmentObject(key);
        if (ao == null && ObjectHelper.isNumber(key)) {
            Integer idx = exchange.getContext().getTypeConverter().tryConvertTo(Integer.class, key);
            if (idx != null) {
                Iterator<?> it = ObjectHelper.createIterator(am.getAttachments().keySet());
                for (int i = 0; i <= idx && it.hasNext(); i++) {
                    if (i == idx) {
                        key = it.next().toString();
                    } else {
                        key = null;
                        it.next();
                    }
                }
                if (key != null) {
                    ao = am.getAttachmentObject(key);
                }
            }
        }
        return ao;
    }

}
