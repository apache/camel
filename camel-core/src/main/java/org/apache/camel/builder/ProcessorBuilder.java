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
package org.apache.camel.builder;

import java.util.Arrays;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Processor;

/**
 * A builder of a number of different {@link Processor} implementations
 *
 * @version 
 */
public final class ProcessorBuilder {

    /**
     * Utility classes should not have a public constructor.
     */
    private ProcessorBuilder() {
    }

    /**
     * Creates a processor which sets the body of the message to the value of the expression
     */
    public static Processor setBody(final Expression expression) {
        return new Processor() {
            public void process(Exchange exchange) {
                Object newBody = expression.evaluate(exchange, Object.class);
                if (exchange.hasOut()) {
                    exchange.getOut().setBody(newBody);
                } else {
                    exchange.getIn().setBody(newBody);
                }
            }

            @Override
            public String toString() {
                return "setBody(" + expression + ")";
            }
        };
    }

    /**
     * Creates a processor which sets the body of the OUT message to the value of the expression
     *
     * @deprecated use {@link #setBody(org.apache.camel.Expression)}
     */
    @Deprecated
    public static Processor setOutBody(final Expression expression) {
        return new Processor() {
            public void process(Exchange exchange) {
                Object newBody = expression.evaluate(exchange, Object.class);
                exchange.getOut().setBody(newBody);
            }

            @Override
            public String toString() {
                return "setOutBody(" + expression + ")";
            }
        };
    }

    /**
     * Creates a processor which sets the body of the FAULT message to the value of the expression
     */
    public static Processor setFaultBody(final Expression expression) {
        return new Processor() {
            public void process(Exchange exchange) {
                Object newBody = expression.evaluate(exchange, Object.class);
                if (exchange.hasOut()) {
                    exchange.getOut().setFault(true);
                    exchange.getOut().setBody(newBody);
                } else {
                    exchange.getIn().setFault(true);
                    exchange.getIn().setBody(newBody);
                }
            }

            @Override
            public String toString() {
                return "setFaultBody(" + expression + ")";
            }
        };
    }

    /**
     * Sets the header on the message.
     */
    public static Processor setHeader(final String name, final Expression expression) {
        return new Processor() {
            public void process(Exchange exchange) {
                Object value = expression.evaluate(exchange, Object.class);
                if (exchange.hasOut()) {
                    exchange.getOut().setHeader(name, value);
                } else {
                    exchange.getIn().setHeader(name, value);
                }
            }

            @Override
            public String toString() {
                return "setHeader(" + name + ", " + expression + ")";
            }
        };
    }

    /**
     * Sets the header on the OUT message
     *
     * @deprecated use {@link #setHeader(String, org.apache.camel.Expression)}
     */
    @Deprecated
    public static Processor setOutHeader(final String name, final Expression expression) {
        return new Processor() {
            public void process(Exchange exchange) {
                Object value = expression.evaluate(exchange, Object.class);
                exchange.getOut().setHeader(name, value);
            }

            @Override
            public String toString() {
                return "setOutHeader(" + name + ", " + expression + ")";
            }
        };
    }

    /**
     * Sets the header on the FAULT message
     */
    public static Processor setFaultHeader(final String name, final Expression expression) {
        return new Processor() {
            public void process(Exchange exchange) {
                Object value = expression.evaluate(exchange, Object.class);
                if (exchange.hasOut()) {
                    exchange.getOut().setFault(true);
                    exchange.getOut().setHeader(name, value);
                } else {
                    exchange.getIn().setFault(true);
                    exchange.getIn().setHeader(name, value);
                }
            }

            @Override
            public String toString() {
                return "setFaultHeader(" + name + ", " + expression + ")";
            }
        };
    }

    /**
     * Sets the property on the exchange
     */
    public static Processor setProperty(final String name, final Expression expression) {
        return new Processor() {
            public void process(Exchange exchange) {
                Object value = expression.evaluate(exchange, Object.class);
                exchange.setProperty(name, value);
            }

            @Override
            public String toString() {
                return "setProperty(" + name + ", " + expression + ")";
            }
        };
    }

    /**
     * Removes the header on the message.
     */
    public static Processor removeHeader(final String name) {
        return new Processor() {
            public void process(Exchange exchange) {
                if (exchange.hasOut()) {
                    exchange.getOut().removeHeader(name);
                } else {
                    exchange.getIn().removeHeader(name);
                }
            }

            @Override
            public String toString() {
                return "removeHeader(" + name +  ")";
            }
        };
    }

    /**
     * Removes the headers on the message
     */
    public static Processor removeHeaders(final String pattern) {
        return new Processor() {
            public void process(Exchange exchange) {
                if (exchange.hasOut()) {
                    exchange.getOut().removeHeaders(pattern);
                } else {
                    exchange.getIn().removeHeaders(pattern);
                }
            }

            @Override
            public String toString() {
                return "removeHeaders(" + pattern +  ")";
            }
        };
    }
    
    /**
     * Removes all headers on the message, except for the ones provided in the <tt>names</tt> parameter
     */
    public static Processor removeHeaders(final String pattern, final String... exceptionPatterns) {
        return new Processor() {
            public void process(Exchange exchange) {
                if (exchange.hasOut()) {
                    exchange.getOut().removeHeaders(pattern, exceptionPatterns);
                } else {
                    exchange.getIn().removeHeaders(pattern, exceptionPatterns);
                }
            }

            @Override
            public String toString() {
                return "removeHeaders(" + pattern + ", " + Arrays.toString(exceptionPatterns) + ")";
            }
        };
    }

    /**
     * Removes the header on the FAULT message (FAULT must be OUT)
     * @deprecated will be removed in the near future. Instead use {@link #removeHeader(String)}
     */
    @Deprecated
    public static Processor removeFaultHeader(final String name) {
        return new Processor() {
            public void process(Exchange exchange) {
                exchange.getOut().setFault(true);
                exchange.getOut().removeHeader(name);
            }

            @Override
            public String toString() {
                return "removeFaultHeader(" + name +  ")";
            }
        };
    }

    /**
     * Removes the property on the exchange
     */
    public static Processor removeProperty(final String name) {
        return new Processor() {
            public void process(Exchange exchange) {
                exchange.removeProperty(name);
            }

            @Override
            public String toString() {
                return "removeProperty(" + name +  ")";
            }
        };
    }
    
    /**
     * Removes the properties on the exchange
     */
    public static Processor removeProperties(final String pattern) {
        return new Processor() {
            public void process(Exchange exchange) {
                exchange.removeProperties(pattern);
            }

            @Override
            public String toString() {
                return "removeProperties(" + pattern +  ")";
            }
        };
    }
    
    /**
     * Removes all properties on the exchange, except for the ones provided in the <tt>names</tt> parameter
     */
    public static Processor removeProperties(final String pattern, final String... exceptionPatterns) {
        return new Processor() {
            public void process(Exchange exchange) {
                exchange.removeProperties(pattern, exceptionPatterns);
            }

            @Override
            public String toString() {
                return "removeProperties(" + pattern + ", " + Arrays.toString(exceptionPatterns) + ")";
            }
        };
    }

    /**
     * Throws an exception
     */
    public static Processor throwException(final Exception ex) {
        return new Processor() {
            public void process(Exchange exchange) throws Exception {
                throw ex;
            }

            @Override
            public String toString() {
                return "throwException(" + ex.toString() +  ")";
            }
        };
    }
}
