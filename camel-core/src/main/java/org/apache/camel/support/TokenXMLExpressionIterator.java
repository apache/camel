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
import java.text.MessageFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * {@link org.apache.camel.Expression} to walk a {@link org.apache.camel.Message} XML body
 * using an {@link java.util.Iterator}, which grabs the content between a XML start and end token,
 * where the end token corresponds implicitly to either the end tag or the self-closing start tag.
 * <p/>
 * The message body must be able to convert to {@link java.io.InputStream} type which is used as stream
 * to access the message body.
 * <p/>
 * Can be used to split big XML files.
 * <p/>
 * This implementation supports inheriting namespaces from a parent/root tag.
 */
public class TokenXMLExpressionIterator extends ExpressionAdapter {
    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("xmlns(:\\w+|)\\s*=\\s*('[^']+'|\"[^\"]+\")");
    private static final String SCAN_TOKEN_NS_PREFIX_REGEX = "([^:<>]{1,15}?:|)";
    private static final String SCAN_BLOCK_TOKEN_REGEX_TEMPLATE = "<{0}(\\s+[^/]*)?/>|<{0}(\\s+[^>]*)?>(?:(?!(</{0}\\s*>)).)*</{0}\\s*>";
    private static final String SCAN_PARENT_TOKEN_REGEX_TEMPLATE = "<{0}(\\s+[^>]*\\s*)?>";
    
    protected final String tagToken;
    protected final String inheritNamespaceToken;

    public TokenXMLExpressionIterator(String tagToken, String inheritNamespaceToken) {
        ObjectHelper.notEmpty(tagToken, "tagToken");
        this.tagToken = tagToken;
        // namespace token is optional
        this.inheritNamespaceToken = inheritNamespaceToken;

        // must be XML tokens
        if (!tagToken.startsWith("<") || !tagToken.endsWith(">")) {
            throw new IllegalArgumentException("XML Tag token must be a valid XML tag, was: " + tagToken);
        }
        if (inheritNamespaceToken != null && (!inheritNamespaceToken.startsWith("<") || !inheritNamespaceToken.endsWith(">"))) {
            throw new IllegalArgumentException("Namespace token must be a valid XML token, was: " + inheritNamespaceToken);
        }
    }

    protected Iterator<?> createIterator(InputStream in, String charset) {
        XMLTokenIterator iterator = new XMLTokenIterator(tagToken, inheritNamespaceToken, in, charset);
        iterator.init();
        return iterator;
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
     * @param exchange   the exchange
     * @param closeStream whether to close the stream before returning from this method.
     * @return the evaluated value
     */
    protected Object doEvaluate(Exchange exchange, boolean closeStream) {
        InputStream in = null;
        try {
            in = exchange.getIn().getMandatoryBody(InputStream.class);
            // we may read from a file, and want to support custom charset defined on the exchange
            String charset = IOHelper.getCharsetName(exchange);
            return createIterator(in, charset);
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
     * Iterator to walk the input stream
     */
    static class XMLTokenIterator implements Iterator<Object>, Closeable {
        final String tagToken;
        final InputStream in;
        final String charset;
        Scanner scanner;
        Object image;

        private final Pattern tagTokenPattern;
        private final String inheritNamespaceToken;
        private Pattern inheritNamespaceTokenPattern;
        private String rootTokenNamespaces;

        XMLTokenIterator(String tagToken, String inheritNamespaceToken, InputStream in, String charset) {
            this.tagToken = tagToken;
            this.in = in;
            this.charset = charset;
          
            // remove any beginning < and ending > as we need to support ns prefixes and attributes, so we use a reg exp patterns
            this.tagTokenPattern = 
                Pattern.compile(MessageFormat.format(SCAN_BLOCK_TOKEN_REGEX_TEMPLATE, 
                                                     SCAN_TOKEN_NS_PREFIX_REGEX + tagToken.substring(1, tagToken.length() - 1)), 
                                                     Pattern.MULTILINE | Pattern.DOTALL);
            
            this.inheritNamespaceToken = inheritNamespaceToken;
            if (inheritNamespaceToken != null) {
                // the inherit namespace token may itself have a namespace prefix
                // the namespaces on the parent tag can be in multi line, so we need to instruct the dot to support multilines
                this.inheritNamespaceTokenPattern = 
                    Pattern.compile(MessageFormat.format(SCAN_PARENT_TOKEN_REGEX_TEMPLATE,
                                                         SCAN_TOKEN_NS_PREFIX_REGEX + inheritNamespaceToken.substring(1, inheritNamespaceToken.length() - 1)), 
                                                         Pattern.MULTILINE | Pattern.DOTALL);
            }
        }

        void init() {
            // use a scanner with the default delimiter
            this.scanner = new Scanner(in, charset);
            this.image = scanner.hasNext() ? (String) next(true) : null;
        }

        String getNext(boolean first) {
            // initialize inherited namespaces on first
            if (first && inheritNamespaceToken != null) {
                rootTokenNamespaces =  getNamespacesFromNamespaceToken(scanner.findWithinHorizon(inheritNamespaceTokenPattern, 0));
            }

            String next = scanner.findWithinHorizon(tagTokenPattern, 0);
            if (next == null) {
                return null;
            }

            // build answer accordingly to whether namespaces should be inherited or not
            // REVISIT should skip the prefixes that are declared within the child itself.
            if (inheritNamespaceToken != null && rootTokenNamespaces != null) {
                String head = ObjectHelper.before(next, ">");
                boolean empty = false;
                if (head.endsWith("/")) {
                    head = head.substring(0, head.length() - 1);
                    empty = true;
                }
                StringBuilder sb = new StringBuilder();
                // append root namespaces to local start token
                // grab the text
                String tail = ObjectHelper.after(next, ">");
                // build result with inherited namespaces
                next = sb.append(head).append(rootTokenNamespaces).append(empty ? "/>" : ">").append(tail).toString();
            }
            
            return next;
        }

        private String getNamespacesFromNamespaceToken(String text) {
            if (text == null) {
                return null;
            }

            // find namespaces (there can be attributes mixed, so we should only grab the namespaces)
            Map<String, String> namespaces = new LinkedHashMap<String, String>();
            Matcher matcher = NAMESPACE_PATTERN.matcher(text);
            while (matcher.find()) {
                String prefix = matcher.group(1);
                String url = matcher.group(2);
                if (ObjectHelper.isEmpty(prefix)) {
                    prefix = "_DEFAULT_";
                } else {
                    // skip leading :
                    prefix = prefix.substring(1);
                }
                namespaces.put(prefix, url);
            }

            // did we find any namespaces
            if (namespaces.isEmpty()) {
                return null;
            }

            // build namespace String
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> entry : namespaces.entrySet()) {
                String key = entry.getKey();
                // note the value is already quoted
                String value = entry.getValue();
                if ("_DEFAULT_".equals(key)) {
                    sb.append(" xmlns=").append(value);
                } else {
                    sb.append(" xmlns:").append(key).append("=").append(value);
                }
            }

            return sb.toString();
        }
        
        @Override
        public boolean hasNext() {
            return image != null;
        }

        @Override
        public Object next() {
            return next(false);
        }

        Object next(boolean first) {
            Object answer = image;
            // calculate next
            if (scanner.hasNext()) {
                image = getNext(first);
            } else {
                image = null;
            }

            if (answer == null) {
                // first time the image may be null
                answer = image;
            }
            return answer;
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
