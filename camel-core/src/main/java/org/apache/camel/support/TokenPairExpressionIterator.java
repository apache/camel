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
package org.apache.camel.support;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Scanner;

import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.util.ObjectHelper;

/**
 * {@link org.apache.camel.Expression} to walk a {@link org.apache.camel.Message} body
 * using an {@link Iterator}, which grabs the content between a start and end token.
 * <p/>
 * The message body must be able to convert to {@link InputStream} type which is used as stream
 * to access the message body.
 * <p/>
 * Can be used to split big XML files
 */
public class TokenPairExpressionIterator extends ExpressionAdapter {

    private final String startToken;
    private final String endToken;

    public TokenPairExpressionIterator(String startToken, String endToken) {
        this.startToken = startToken;
        this.endToken = endToken;
        ObjectHelper.notEmpty(startToken, "startToken");
        ObjectHelper.notEmpty(endToken, "endToken");
    }

    @Override
    public Object evaluate(Exchange exchange) {
        try {
            InputStream in = exchange.getIn().getMandatoryBody(InputStream.class);
            return new TokenPairIterator(startToken, endToken, in);
        } catch (InvalidPayloadException e) {
            exchange.setException(e);
            return null;
        }
    }

    @Override
    public String toString() {
        return "tokenize[body() using tokens: " + startToken + "..." + endToken + "]";
    }

    /**
     * Iterator to walk the input stream
     */
    private static final class TokenPairIterator implements Iterator, Closeable {

        private final String startToken;
        private final String endToken;
        private final Scanner scanner;
        private Object image;

        private TokenPairIterator(String startToken, String endToken, InputStream in) {
            this.startToken = startToken;
            this.endToken = endToken;
            // use end token as delimiter
            this.scanner = new Scanner(in).useDelimiter(endToken);
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

        private Object getNext() {
            String next = scanner.next();

            // only grab text after the start token
            if (next != null && next.contains(startToken)) {
                next = ObjectHelper.after(next, startToken);

                // include tokens in answer
                if (next != null) {
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
