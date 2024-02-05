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
package org.apache.camel.support.builder;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.camel.support.LanguageSupport;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.Scanner;
import org.apache.camel.util.StringHelper;

/**
 * {@link org.apache.camel.Expression} to walk a {@link org.apache.camel.Message} body using an {@link Iterator}, which
 * grabs the content between a start and end token.
 * <p/>
 * The message body must be able to convert to {@link InputStream} type which is used as stream to access the message
 * body.
 * <p/>
 * For splitting XML files use {@link TokenXMLExpressionIterator} instead.
 */
public class TokenPairExpressionIterator extends ExpressionAdapter {

    protected final String startToken;
    protected final String endToken;
    protected final boolean includeTokens;
    private final Expression source;
    private Expression startExp;
    private Expression endExp;

    public TokenPairExpressionIterator(String startToken, String endToken, boolean includeTokens) {
        this(null, startToken, endToken, includeTokens);
    }

    public TokenPairExpressionIterator(Expression source, String startToken, String endToken, boolean includeTokens) {
        StringHelper.notEmpty(startToken, "startToken");
        StringHelper.notEmpty(endToken, "endToken");
        this.startToken = startToken;
        this.endToken = endToken;
        this.includeTokens = includeTokens;
        this.source = source;
    }

    @Override
    public void init(CamelContext context) {
        if (LanguageSupport.hasSimpleFunction(startToken)) {
            startExp = context.resolveLanguage("simple").createExpression(startToken);
            startExp.init(context);
        }
        if (LanguageSupport.hasSimpleFunction(endToken)) {
            endExp = context.resolveLanguage("simple").createExpression(endToken);
            endExp.init(context);
        }
    }

    @Override
    public boolean matches(Exchange exchange) {
        // as a predicate we must close the stream, as we do not return an iterator that can be used
        // afterwards to iterate the input stream
        Object value = doEvaluate(exchange, true);
        return ObjectHelper.evaluateValuePredicate(value);
    }

    @Override
    public Object evaluate(Exchange exchange) {
        // as we return an iterator to access the input stream, we should not close it
        return doEvaluate(exchange, false);
    }

    /**
     * Strategy to evaluate the exchange
     *
     * @param  exchange    the exchange
     * @param  closeStream whether to close the stream before returning from this method.
     * @return             the evaluated value
     */
    protected Object doEvaluate(Exchange exchange, boolean closeStream) {
        InputStream in = null;
        try {
            if (source != null) {
                in = source.evaluate(exchange, InputStream.class);
            } else {
                in = exchange.getIn().getBody(InputStream.class);
            }
            if (in == null) {
                throw new InvalidPayloadException(exchange, InputStream.class);
            }
            // we may read from a file, and want to support custom charset defined on the exchange
            String charset = ExchangeHelper.getCharsetName(exchange);
            return createIterator(exchange, in, charset);
        } catch (InvalidPayloadException e) {
            exchange.setException(e);
            // must close input stream
            IOHelper.close(in);
            return null;
        } finally {
            if (closeStream) {
                IOHelper.close(in);
            }
        }
    }

    /**
     * Strategy to create the iterator
     *
     * @param  exchange the exchange
     * @param  in       input stream to iterate
     * @param  charset  charset
     * @return          the iterator
     */
    protected Iterator<?> createIterator(Exchange exchange, InputStream in, String charset) {
        String start = startToken;
        if (startExp != null) {
            start = startExp.evaluate(exchange, String.class);
        }
        String end = endToken;
        if (endExp != null) {
            end = endExp.evaluate(exchange, String.class);
        }
        TokenPairIterator iterator = new TokenPairIterator(start, end, includeTokens, in, charset);
        iterator.init();
        return iterator;
    }

    @Override
    public String toString() {
        return "tokenize[body() using tokens: " + startToken + "..." + endToken + "]";
    }

    /**
     * Iterator to walk the input stream
     */
    static class TokenPairIterator implements Iterator<Object>, Closeable {

        final String startToken;
        String scanStartToken;
        final String endToken;
        String scanEndToken;
        final boolean includeTokens;
        final InputStream in;
        final String charset;
        Scanner scanner;
        Object image;

        TokenPairIterator(String startToken, String endToken, boolean includeTokens, InputStream in, String charset) {
            this.startToken = startToken;
            this.endToken = endToken;
            this.includeTokens = includeTokens;
            this.in = in;
            this.charset = charset;

            // make sure [ and ] is escaped as we use scanner which is reg exp based
            // where [ and ] have special meaning
            scanStartToken = startToken;
            if (scanStartToken.startsWith("[")) {
                scanStartToken = "\\" + scanStartToken;
            }
            if (scanStartToken.endsWith("]")) {
                scanStartToken = scanStartToken.substring(0, startToken.length() - 1) + "\\]";
            }
            scanEndToken = endToken;
            if (scanEndToken.startsWith("[")) {
                scanEndToken = "\\" + scanEndToken;
            }
            if (scanEndToken.endsWith("]")) {
                scanEndToken = scanEndToken.substring(0, scanEndToken.length() - 1) + "\\]";
            }
        }

        void init() {
            // use end token as delimiter
            this.scanner = new Scanner(in, charset, scanEndToken);
            // this iterator will do look ahead as we may have data
            // after the last end token, which the scanner would find
            // so we need to be one step ahead of the scanner
            this.image = scanner.hasNext() ? next() : null;
        }

        @Override
        public boolean hasNext() {
            return image != null;
        }

        @Override
        public Object next() {
            Object answer = image;
            // calculate next
            if (scanner.hasNext()) {
                image = getNext();
            } else {
                image = null;
            }

            if (answer == null) {
                // first time the image may be null
                answer = image;
            }
            return answer;
        }

        Object getNext() {
            String next = scanner.next();

            // only grab text after the start token
            if (next != null && next.contains(startToken)) {
                next = StringHelper.after(next, startToken);

                // include tokens in answer
                if (next != null && includeTokens) {
                    StringBuilder sb = new StringBuilder();
                    next = sb.append(startToken).append(next).append(endToken).toString();
                }
            } else {
                // must have start token, otherwise we have reached beyond last tokens
                // and should not return more data
                return null;
            }

            return next;
        }

        @Override
        public void remove() {
            // noop
        }

        @Override
        public void close() throws IOException {
            scanner.close();
        }
    }

}
