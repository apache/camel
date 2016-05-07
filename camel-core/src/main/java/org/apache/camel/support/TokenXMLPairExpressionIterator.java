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

import java.io.InputStream;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.Exchange;
import org.apache.camel.language.simple.SimpleLanguage;
import org.apache.camel.util.ObjectHelper;

/**
 * {@link org.apache.camel.Expression} to walk a {@link org.apache.camel.Message} XML body
 * using an {@link java.util.Iterator}, which grabs the content between a XML start and end token.
 * <p/>
 * The message body must be able to convert to {@link java.io.InputStream} type which is used as stream
 * to access the message body.
 * <p/>
 * Can be used to split big XML files.
 * <p/>
 * This implementation supports inheriting namespaces from a parent/root tag.
 *
 * @deprecated use {@link TokenXMLExpressionIterator} instead.
 */
@Deprecated
public class TokenXMLPairExpressionIterator extends TokenPairExpressionIterator {

    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("xmlns(:\\w+|)=\\\"(.*?)\\\"");
    private static final String SCAN_TOKEN_REGEX = "(\\s+.*?|)>";
    private static final String SCAN_TOKEN_NS_PREFIX_REGEX = "(.{1,15}?:|)";
    protected final String inheritNamespaceToken;

    public TokenXMLPairExpressionIterator(String startToken, String endToken, String inheritNamespaceToken) {
        super(startToken, endToken, true);
        // namespace token is optional
        this.inheritNamespaceToken = inheritNamespaceToken;
    }

    @Override
    protected Iterator<?> createIterator(Exchange exchange, InputStream in, String charset) {
        String start = startToken;
        if (SimpleLanguage.hasSimpleFunction(start)) {
            start = SimpleLanguage.expression(start).evaluate(exchange, String.class);
        }
        String end = endToken;
        if (SimpleLanguage.hasSimpleFunction(end)) {
            end = SimpleLanguage.expression(end).evaluate(exchange, String.class);
        }
        String inherit = inheritNamespaceToken;
        if (inherit != null && SimpleLanguage.hasSimpleFunction(inherit)) {
            inherit = SimpleLanguage.expression(inherit).evaluate(exchange, String.class);
        }

        // must be XML tokens
        if (!start.startsWith("<") || !start.endsWith(">")) {
            throw new IllegalArgumentException("Start token must be a valid XML token, was: " + start);
        }
        if (!end.startsWith("<") || !end.endsWith(">")) {
            throw new IllegalArgumentException("End token must be a valid XML token, was: " + end);
        }
        if (inherit != null && (!inherit.startsWith("<") || !inherit.endsWith(">"))) {
            throw new IllegalArgumentException("Namespace token must be a valid XML token, was: " + inherit);
        }

        XMLTokenPairIterator iterator = new XMLTokenPairIterator(start, end, inherit, in, charset);
        iterator.init();
        return iterator;
    }

    /**
     * Iterator to walk the input stream
     */
    static class XMLTokenPairIterator extends TokenPairIterator {

        private final Pattern startTokenPattern;
        private final String scanEndToken;
        private final String inheritNamespaceToken;
        private Pattern inheritNamespaceTokenPattern;
        private String rootTokenNamespaces;

        XMLTokenPairIterator(String startToken, String endToken, String inheritNamespaceToken, InputStream in, String charset) {
            super(startToken, endToken, true, in, charset);

            // remove any beginning < and ending > as we need to support ns prefixes and attributes, so we use a reg exp patterns
            StringBuilder tokenSb = new StringBuilder("<").append(SCAN_TOKEN_NS_PREFIX_REGEX).
                                append(startToken.substring(1, startToken.length() - 1)).append(SCAN_TOKEN_REGEX);
            this.startTokenPattern = Pattern.compile(tokenSb.toString());
            
            tokenSb = new StringBuilder("</").append(SCAN_TOKEN_NS_PREFIX_REGEX).
                                append(endToken.substring(2, endToken.length() - 1)).append(SCAN_TOKEN_REGEX);
            this.scanEndToken = tokenSb.toString();
            
            this.inheritNamespaceToken = inheritNamespaceToken;
            if (inheritNamespaceToken != null) {
                // the inherit namespace token may itself have a namespace prefix
                tokenSb = new StringBuilder("<").append(SCAN_TOKEN_NS_PREFIX_REGEX).
                                append(inheritNamespaceToken.substring(1, inheritNamespaceToken.length() - 1)).append(SCAN_TOKEN_REGEX);  
                // the namespaces on the parent tag can be in multi line, so we need to instruct the dot to support multilines
                this.inheritNamespaceTokenPattern = Pattern.compile(tokenSb.toString(), Pattern.MULTILINE | Pattern.DOTALL);
            }
        }

        @Override
        void init() {
            // use scan end token as delimiter which supports attributes/namespaces
            this.scanner = new Scanner(in, charset).useDelimiter(scanEndToken);
            // this iterator will do look ahead as we may have data
            // after the last end token, which the scanner would find
            // so we need to be one step ahead of the scanner
            this.image = scanner.hasNext() ? (String) next(true) : null;
        }

        @Override
        String getNext(boolean first) {
            String next = scanner.next();
            if (next == null) {
                return null;
            }

            // initialize inherited namespaces on first
            if (first && inheritNamespaceToken != null) {
                rootTokenNamespaces = getNamespacesFromNamespaceToken(next);
            }

            // make sure next is positioned at start token as we can have leading data
            // or we reached EOL and there is no more start tags
            Matcher matcher = startTokenPattern.matcher(next);
            if (!matcher.find()) {
                return null;
            } else {
                int index = matcher.start();
                next = next.substring(index);
            }

            // make sure the end tag matches the begin tag if the tag has a namespace prefix
            String tag = ObjectHelper.before(next, ">");
            StringBuilder endTagSb = new StringBuilder("</");
            int firstSpaceIndex = tag.indexOf(" ");
            if (firstSpaceIndex > 0) {
                endTagSb.append(tag.substring(1, firstSpaceIndex)).append(">");
            } else {
                endTagSb.append(tag.substring(1, tag.length())).append(">");
            }
            
            // build answer accordingly to whether namespaces should be inherited or not
            StringBuilder sb = new StringBuilder();
            if (inheritNamespaceToken != null && rootTokenNamespaces != null) {
                // append root namespaces to local start token
                // grab the text
                String text = ObjectHelper.after(next, ">");
                // build result with inherited namespaces
                next = sb.append(tag).append(rootTokenNamespaces).append(">").append(text).append(endTagSb.toString()).toString();
            } else {
                next = sb.append(next).append(endTagSb.toString()).toString();
            }

            return next;
        }

        private String getNamespacesFromNamespaceToken(String text) {
            if (text == null) {
                return null;
            }

            // grab the namespace tag
            Matcher mat = inheritNamespaceTokenPattern.matcher(text);
            if (mat.find()) {
                text = mat.group(0);
            } else {
                // cannot find namespace tag
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
                String value = entry.getValue();
                if ("_DEFAULT_".equals(key)) {
                    sb.append(" xmlns=\"").append(value).append("\"");
                } else {
                    sb.append(" xmlns:").append(key).append("=\"").append(value).append("\"");
                }
            }

            return sb.toString();
        }
    }

}
