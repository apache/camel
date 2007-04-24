/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.builder;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Processor;

/**
 * A builder of a number of different {@link Processor} implementations
 *
 * @version $Revision: 1.1 $
 */
public class ProcessorBuilder {

    /**
     * Creates a processor which sets the body of the IN message to the value of the expression
     */
    public static <E extends Exchange> Processor<E> setBody(final Expression<E> expression) {
        return new Processor<E>() {
            public void process(E exchange) {
                Object newBody = expression.evaluate(exchange);
                exchange.getIn().setBody(newBody);
            }

            @Override
            public String toString() {
                return "setBody(" + expression + ")";
            }
        };
    }

    /**
     * Creates a processor which sets the body of the IN message to the value of the expression
     */
    public static <E extends Exchange> Processor<E> setOutBody(final Expression<E> expression) {
        return new Processor<E>() {
            public void process(E exchange) {
                Object newBody = expression.evaluate(exchange);
                exchange.getOut().setBody(newBody);
            }

            @Override
            public String toString() {
                return "setOutBody(" + expression + ")";
            }
        };
    }

    /**
     * Sets the header on the IN message
     */
    public static <E extends Exchange> Processor<E> setHeader(final String name, final Expression<E> expression) {
        return new Processor<E>() {
            public void process(E exchange) {
                Object value = expression.evaluate(exchange);
                exchange.getIn().setHeader(name, value);
            }

            @Override
            public String toString() {
                return "setHeader(" + name + ", " + expression + ")";
            }
        };
    }

    /**
     * Sets the header on the OUT message
     */
    public static <E extends Exchange> Processor<E> setOutHeader(final String name, final Expression<E> expression) {
        return new Processor<E>() {
            public void process(E exchange) {
                Object value = expression.evaluate(exchange);
                exchange.getOut().setHeader(name, value);
            }

            @Override
            public String toString() {
                return "setOutHeader(" + name + ", " + expression + ")";
            }
        };
    }

    /**
     * Sets the property on the exchange
     */
    public static <E extends Exchange> Processor<E> setProperty(final String name, final Expression<E> expression) {
        return new Processor<E>() {
            public void process(E exchange) {
                Object value = expression.evaluate(exchange);
                exchange.setProperty(name, value);
            }

            @Override
            public String toString() {
                return "setProperty(" + name + ", " + expression + ")";
            }
        };
    }
}
