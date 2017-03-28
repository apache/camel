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
package org.apache.camel.model;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.ExchangePattern;
import org.apache.camel.Expression;
import org.apache.camel.NoSuchLanguageException;
import org.apache.camel.Processor;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.processor.SendDynamicProcessor;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.ObjectHelper;

/**
 * Sends the message to a dynamic endpoint
 * <p/>
 * You can specify multiple languages in the uri separated by the plus sign, such as <tt>mock:+language:xpath:/order/@uri</tt>
 * where <tt>mock:</tt> would be a prefix to a xpath expression.
 * <p/>
 * For more dynamic behavior use <a href="http://camel.apache.org/recipient-list.html">Recipient List</a> or
 * <a href="http://camel.apache.org/dynamic-router.html">Dynamic Router</a> EIP instead.
 */
@Metadata(label = "eip,endpoint,routing")
@XmlRootElement(name = "toD")
@XmlAccessorType(XmlAccessType.FIELD)
public class ToDynamicDefinition extends NoOutputDefinition<ToDynamicDefinition> {

    private static final Pattern RAW_PATTERN = Pattern.compile("RAW\\([^\\)]+\\)");

    @XmlAttribute @Metadata(required = "true")
    private String uri;
    @XmlAttribute
    private ExchangePattern pattern;
    @XmlAttribute
    private Integer cacheSize;
    @XmlAttribute
    private Boolean ignoreInvalidEndpoint;

    public ToDynamicDefinition() {
    }

    public ToDynamicDefinition(String uri) {
        this.uri = uri;
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        ObjectHelper.notEmpty(uri, "uri", this);

        Expression exp = createExpression(routeContext);

        SendDynamicProcessor processor = new SendDynamicProcessor(uri, exp);
        processor.setCamelContext(routeContext.getCamelContext());
        processor.setPattern(pattern);
        if (cacheSize != null) {
            processor.setCacheSize(cacheSize);
        }
        if (ignoreInvalidEndpoint != null) {
            processor.setIgnoreInvalidEndpoint(ignoreInvalidEndpoint);
        }
        return processor;
    }

    protected Expression createExpression(RouteContext routeContext) {
        List<Expression> list = new ArrayList<Expression>();

        String[] parts = safeSplitRaw(uri);
        for (String part : parts) {
            // the part may have optional language to use, so you can mix languages
            String value = ObjectHelper.after(part, "language:");
            if (value != null) {
                String before = ObjectHelper.before(value, ":");
                String after = ObjectHelper.after(value, ":");
                if (before != null && after != null) {
                    // maybe its a language, must have language: as prefix
                    try {
                        Language partLanguage = routeContext.getCamelContext().resolveLanguage(before);
                        if (partLanguage != null) {
                            Expression exp = partLanguage.createExpression(after);
                            list.add(exp);
                            continue;
                        }
                    } catch (NoSuchLanguageException e) {
                        // ignore
                    }
                }
            }
            // fallback and use simple language
            Language lan = routeContext.getCamelContext().resolveLanguage("simple");
            Expression exp = lan.createExpression(part);
            list.add(exp);
        }

        Expression exp;
        if (list.size() == 1) {
            exp = list.get(0);
        } else {
            exp = ExpressionBuilder.concatExpression(list);
        }

        return exp;
    }

    @Override
    public String toString() {
        return "DynamicTo[" + getLabel() + "]";
    }

    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * Sets the optional {@link ExchangePattern} used to invoke this endpoint
     */
    public ToDynamicDefinition pattern(ExchangePattern pattern) {
        setPattern(pattern);
        return this;
    }

    /**
     * Sets the maximum size used by the {@link org.apache.camel.impl.ConsumerCache} which is used to cache and reuse producers.
     *
     * @param cacheSize  the cache size, use <tt>0</tt> for default cache size, or <tt>-1</tt> to turn cache off.
     * @return the builder
     */
    public ToDynamicDefinition cacheSize(int cacheSize) {
        setCacheSize(cacheSize);
        return this;
    }

    /**
     * Ignore the invalidate endpoint exception when try to create a producer with that endpoint
     *
     * @return the builder
     */
    public ToDynamicDefinition ignoreInvalidEndpoint() {
        setIgnoreInvalidEndpoint(true);
        return this;
    }

    // Properties
    // -------------------------------------------------------------------------

    public String getUri() {
        return uri;
    }

    /**
     * The uri of the endpoint to send to. The uri can be dynamic computed using the {@link org.apache.camel.language.simple.SimpleLanguage} expression.
     */
    public void setUri(String uri) {
        this.uri = uri;
    }

    public ExchangePattern getPattern() {
        return pattern;
    }

    public void setPattern(ExchangePattern pattern) {
        this.pattern = pattern;
    }

    public Integer getCacheSize() {
        return cacheSize;
    }

    public void setCacheSize(Integer cacheSize) {
        this.cacheSize = cacheSize;
    }

    public Boolean getIgnoreInvalidEndpoint() {
        return ignoreInvalidEndpoint;
    }

    public void setIgnoreInvalidEndpoint(Boolean ignoreInvalidEndpoint) {
        this.ignoreInvalidEndpoint = ignoreInvalidEndpoint;
    }

    // Utilities
    // -------------------------------------------------------------------------

    private static class Pair {
        int left;
        int right;
        Pair(int left, int right) {
            this.left = left;
            this.right = right;
        }
    }

    private static List<Pair> checkRAW(String s) {
        Matcher matcher = RAW_PATTERN.matcher(s);
        List<Pair> answer = new ArrayList<Pair>();
        // Check all occurrences
        while (matcher.find()) {
            answer.add(new Pair(matcher.start(), matcher.end() - 1));
        }
        return answer;
    }

    private static boolean isRaw(int index, List<Pair>pairs) {
        for (Pair pair : pairs) {
            if (index < pair.left) {
                return false;
            } else {
                if (index >= pair.left) {
                    if (index <= pair.right) {
                        return true;
                    } else {
                        continue;
                    }
                }
            }
        }
        return false;
    }

    /**
     * We need to split the string safely for each + sign, but avoid splitting within RAW(...).
     */
    private static String[] safeSplitRaw(String s) {
        List<String> list = new ArrayList<>();

        if (!s.contains("+")) {
            // no plus sign so there is only one part, so no need to split
            list.add(s);
        } else {
            // there is a plus sign so we need to split in a safe manner
            List<Pair> rawPairs = checkRAW(s);
            StringBuilder sb = new StringBuilder();
            char chars[] = s.toCharArray();
            for (int i = 0; i < chars.length; i++) {
                char ch = chars[i];
                if (ch != '+' || isRaw(i, rawPairs)) {
                    sb.append(ch);
                } else {
                    list.add(sb.toString());
                    sb.setLength(0);
                }
            }
            // any leftover?
            if (sb.length() > 0) {
                list.add(sb.toString());
                sb.setLength(0);
            }
        }

        return list.toArray(new String[list.size()]);
    }

}
