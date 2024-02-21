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
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.camel.support.LanguageSupport;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;

/**
 * {@link org.apache.camel.Expression} to walk a {@link org.apache.camel.Message} XML body using an
 * {@link java.util.Iterator}, which grabs the content between a XML start and end token, where the end token
 * corresponds implicitly to either the end tag or the self-closing start tag.
 * <p/>
 * The message body must be able to convert to {@link java.io.InputStream} type which is used as stream to access the
 * message body.
 * <p/>
 * Can be used to split big XML files.
 * <p/>
 * This implementation supports inheriting namespaces from a parent/root tag.
 */
public class TokenXMLExpressionIterator extends ExpressionAdapter {
    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("xmlns(:\\w+|)\\s*=\\s*('[^']+'|\"[^\"]+\")");
    private static final String SCAN_TOKEN_NS_PREFIX_REGEX = "([^:<>]{1,15}?:|)";
    private static final String SCAN_BLOCK_TOKEN_REGEX_TEMPLATE
            = "<{0}(\\s+[^>]*)?/>|<{0}(\\s+[^>]*)?>(?:(?!(</{0}\\s*>)).)*</{0}\\s*>";
    private static final String SCAN_PARENT_TOKEN_REGEX_TEMPLATE = "<{0}(\\s+[^>]*\\s*)?>";
    private static final String OPTION_WRAP_TOKEN = "<*>";
    private static final String NAMESPACE_SEPERATOR = " ";

    protected final String tagToken;
    protected final String inheritNamespaceToken;
    protected Expression source;

    public TokenXMLExpressionIterator(String tagToken, String inheritNamespaceToken) {
        this(null, tagToken, inheritNamespaceToken);
    }

    public TokenXMLExpressionIterator(Expression source, String tagToken, String inheritNamespaceToken) {
        StringHelper.notEmpty(tagToken, "tagToken");
        this.tagToken = tagToken;
        // namespace token is optional
        this.inheritNamespaceToken = inheritNamespaceToken;
        this.source = source;
    }

    protected Iterator<?> createIterator(Exchange exchange, InputStream in, String charset) {
        String tag = tagToken;
        if (LanguageSupport.hasSimpleFunction(tag)) {
            tag = exchange.getContext().resolveLanguage("simple").createExpression(tag).evaluate(exchange, String.class);
        }
        String inherit = inheritNamespaceToken;
        if (LanguageSupport.hasSimpleFunction(inherit)) {
            inherit = exchange.getContext().resolveLanguage("simple").createExpression(inherit).evaluate(exchange,
                    String.class);
        }

        // must be XML tokens
        if (!tag.startsWith("<")) {
            tag = "<" + tag;
        }
        if (!tag.endsWith(">")) {
            tag = tag + ">";
        }

        if (inherit != null) {
            if (!inherit.startsWith("<")) {
                inherit = "<" + inherit;
            }
            if (!inherit.endsWith(">")) {
                inherit = inherit + ">";
            }
        }

        // must be XML tokens
        if (!tag.startsWith("<") || !tag.endsWith(">")) {
            throw new IllegalArgumentException("XML Tag token must be a valid XML tag, was: " + tag);
        }
        if (inherit != null && (!inherit.startsWith("<") || !inherit.endsWith(">"))) {
            throw new IllegalArgumentException("Namespace token must be a valid XML token, was: " + inherit);
        }

        XMLTokenIterator iterator = new XMLTokenIterator(tag, inherit, in, charset);
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
        private final boolean wrapToken;
        private Pattern inheritNamespaceTokenPattern;
        private String[] rootTokenNamespaces;
        private String wrapHead;
        private String wrapTail;

        XMLTokenIterator(String tagToken, String inheritNamespaceToken, InputStream in, String charset) {
            this.tagToken = tagToken;
            this.charset = charset;

            // remove any beginning < and ending > as we need to support ns prefixes and attributes, so we use a reg exp patterns
            this.tagTokenPattern = Pattern.compile(MessageFormat.format(SCAN_BLOCK_TOKEN_REGEX_TEMPLATE,
                    SCAN_TOKEN_NS_PREFIX_REGEX + tagToken.substring(1, tagToken.length() - 1)),
                    Pattern.MULTILINE | Pattern.DOTALL);

            this.inheritNamespaceToken = inheritNamespaceToken;
            if (OPTION_WRAP_TOKEN.equals(inheritNamespaceToken)) {
                this.wrapToken = true;
                this.in = new RecordableInputStream(in, charset);
            } else {
                this.wrapToken = false;
                this.in = in;
                if (inheritNamespaceToken != null) {
                    // the inherit namespace token may itself have a namespace prefix
                    // the namespaces on the parent tag can be in multi line, so we need to instruct the dot to support multilines
                    this.inheritNamespaceTokenPattern = Pattern.compile(MessageFormat.format(SCAN_PARENT_TOKEN_REGEX_TEMPLATE,
                            SCAN_TOKEN_NS_PREFIX_REGEX + inheritNamespaceToken.substring(1,
                                    inheritNamespaceToken.length() - 1)),
                            Pattern.MULTILINE | Pattern.DOTALL);
                }
            }
        }

        void init() {
            // use a scanner with the default delimiter
            this.scanner = new Scanner(in, charset);
            this.image = scanner.hasNext() ? (String) next(true) : null;
        }

        String getNext(boolean first) {
            // initialize inherited namespaces on first
            if (first && inheritNamespaceToken != null && !wrapToken) {
                rootTokenNamespaces
                        = getNamespacesFromNamespaceTokenSplitter(scanner.findWithinHorizon(inheritNamespaceTokenPattern, 0));
            }

            String next = scanner.findWithinHorizon(tagTokenPattern, 0);
            if (next == null) {
                return null;
            }
            if (first && wrapToken) {
                MatchResult mres = scanner.match();
                wrapHead = ((RecordableInputStream) in).getText(mres.start());
                wrapTail = buildXMLTail(wrapHead);
            }

            // build answer accordingly to whether namespaces should be inherited or not
            if (inheritNamespaceToken != null && rootTokenNamespaces != null) {
                String head = StringHelper.before(next, ">");
                boolean empty = false;
                if (head.endsWith("/")) {
                    head = head.substring(0, head.length() - 1);
                    empty = true;
                }
                StringBuilder sb = new StringBuilder();
                // append root namespaces to local start token
                // grab the text
                String tail = StringHelper.after(next, ">");
                // build result with inherited namespaces and skip the prefixes that are declared within the child itself.
                next = sb.append(head).append(getMissingInherritNamespaces(head)).append(empty ? "/>" : ">").append(tail)
                        .toString();
            } else if (wrapToken) {
                // wrap the token
                StringBuilder sb = new StringBuilder();
                next = sb.append(wrapHead).append(next).append(wrapTail).toString();
            }

            return next;
        }

        private String getMissingInherritNamespaces(final String text) {
            final StringBuilder sb = new StringBuilder();
            if (text != null) {
                boolean first = true;
                final String[] containedNamespaces = getNamespacesFromNamespaceTokenSplitter(text);
                for (final String rn : rootTokenNamespaces) {
                    boolean nsExists = false;
                    for (final String cn : containedNamespaces) {
                        if (rn.equals(cn)) {
                            nsExists = true;
                            // already existing namespace in child were found we need a separator, so we set first = false
                            if (first) {
                                first = false;
                            }
                            break;
                        }
                    }
                    if (!nsExists) {
                        sb.append(first ? rn : NAMESPACE_SEPERATOR + rn);
                        if (first) {
                            first = false;
                        }
                    }
                }
            }
            return sb.toString();
        }

        private String[] getNamespacesFromNamespaceTokenSplitter(final String text) {
            final String namespaces = getNamespacesFromNamespaceToken(text);
            return namespaces == null ? new String[0] : namespaces.split(NAMESPACE_SEPERATOR);
        }

        private String getNamespacesFromNamespaceToken(String text) {
            if (text == null) {
                return null;
            }

            // find namespaces (there can be attributes mixed, so we should only grab the namespaces)
            Map<String, String> namespaces = new LinkedHashMap<>();
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

    private static String buildXMLTail(String xmlhead) {
        // assume the input text is a portion of a well-formed xml
        List<String> tags = new ArrayList<>();
        int p = 0;
        while (p < xmlhead.length()) {
            p = xmlhead.indexOf('<', p);
            if (p < 0) {
                break;
            }
            int nc = xmlhead.charAt(p + 1);
            if (nc == '?') {
                p++;
                continue;
            } else if (nc == '/') {
                p++;
                tags.remove(tags.size() - 1);
            } else {
                final int ep = xmlhead.indexOf('>', p);
                if (xmlhead.charAt(ep - 1) == '/') {
                    p++;
                    continue;
                }
                final int sp = xmlhead.substring(p, ep).indexOf(' ');
                tags.add(xmlhead.substring(p + 1, sp > 0 ? p + sp : ep));
                p = ep;
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = tags.size() - 1; i >= 0; i--) {
            sb.append("</").append(tags.get(i)).append(">");
        }
        return sb.toString();
    }
}
