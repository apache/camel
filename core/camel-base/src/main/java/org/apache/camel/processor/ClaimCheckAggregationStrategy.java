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
package org.apache.camel.processor;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link AggregationStrategy} used by the {@link ClaimCheckProcessor} EIP.
 * <p/>
 * This strategy supports the following include rules syntax:
 * <ul>
 *     <li>body</li> - to aggregate the message body
 *     <li>headers</li> - to aggregate all the message headers
 *     <li>header:pattern</li> - to aggregate all the message headers that matches the pattern.
 *     The pattern syntax is documented by: {@link PatternHelper#matchPattern(String, String)}.
 * </ul>
 * You can specify multiple rules separated by comma. For example to include the message body and all headers starting with foo
 * <tt>body,header:foo*</tt>.
 * If the include rule is specified as empty or as wildcard then everything is merged.
 * If you have configured both include and exclude then exclude take precedence over include.
 */
public class ClaimCheckAggregationStrategy implements AggregationStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(ClaimCheckAggregationStrategy.class);
    private String filter;

    public ClaimCheckAggregationStrategy() {
    }

    public String getFilter() {
        return filter;
    }

    public void setFilter(String filter) {
        this.filter = filter;
    }

    @Override
    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        if (newExchange == null) {
            return oldExchange;
        }

        if (org.apache.camel.util.ObjectHelper.isEmpty(filter) || "*".equals(filter)) {
            // grab everything
            oldExchange.getMessage().setBody(newExchange.getMessage().getBody());
            LOG.trace("Including: body");
            if (newExchange.getMessage().hasHeaders()) {
                oldExchange.getMessage().getHeaders().putAll(newExchange.getMessage().getHeaders());
                LOG.trace("Including: headers");
            }
            return oldExchange;
        }

        // body is by default often included
        if (isBodyEnabled()) {
            oldExchange.getMessage().setBody(newExchange.getMessage().getBody());
            LOG.trace("Including: body");
        }

        // headers is by default often included
        if (isHeadersEnabled()) {
            if (newExchange.getMessage().hasHeaders()) {
                oldExchange.getMessage().getHeaders().putAll(newExchange.getMessage().getHeaders());
                LOG.trace("Including: headers");
            }
        }

        // filter specific header if they are somehow enabled by the filter
        if (hasHeaderPatterns()) {
            boolean excludeOnly = isExcludeOnlyHeaderPatterns();
            for (Map.Entry<String, Object> header : newExchange.getMessage().getHeaders().entrySet()) {
                String key = header.getKey();
                if (hasHeaderPattern(key)) {
                    boolean include = isIncludedHeader(key);
                    boolean exclude = isExcludedHeader(key);
                    if (include) {
                        LOG.trace("Including: header:{}", key);
                        oldExchange.getMessage().getHeaders().put(key, header.getValue());
                    } else if (exclude) {
                        LOG.trace("Excluding: header:{}", key);
                    } else {
                        LOG.trace("Skipping: header:{}", key);
                    }
                } else if (excludeOnly) {
                    LOG.trace("Including: header:{}", key);
                    oldExchange.getMessage().getHeaders().put(key, header.getValue());
                }
            }
        }

        // filter body and all headers
        if (org.apache.camel.util.ObjectHelper.isNotEmpty(filter)) {
            Iterable<?> it = ObjectHelper.createIterable(filter, ",");
            for (Object k : it) {
                String part = k.toString();
                if (("body".equals(part) || "+body".equals(part)) && !"-body".equals(part)) {
                    oldExchange.getMessage().setBody(newExchange.getMessage().getBody());
                    LOG.trace("Including: body");
                } else if (("headers".equals(part) || "+headers".equals(part)) && !"-headers".equals(part)) {
                    oldExchange.getMessage().getHeaders().putAll(newExchange.getMessage().getHeaders());
                    LOG.trace("Including: headers");
                }
            }
        }

        // filter with remove (--) take precedence at the end
        Iterable<?> it = ObjectHelper.createIterable(filter, ",");
        for (Object k : it) {
            String part = k.toString();
            if ("--body".equals(part)) {
                oldExchange.getMessage().setBody(null);
            } else if ("--headers".equals(part)) {
                oldExchange.getMessage().getHeaders().clear();
            } else if (part.startsWith("--header:")) {
                // pattern matching for headers, eg header:foo, header:foo*, header:(foo|bar)
                String after = StringHelper.after(part, "--header:");
                Iterable<?> i = ObjectHelper.createIterable(after, ",");
                Set<String> toRemoveKeys = new HashSet<>();
                for (Object o : i) {
                    String pattern = o.toString();
                    for (Map.Entry<String, Object> header : oldExchange.getMessage().getHeaders().entrySet()) {
                        String key = header.getKey();
                        boolean matched = PatternHelper.matchPattern(key, pattern);
                        if (matched) {
                            toRemoveKeys.add(key);
                        }
                    }
                }
                for (String key : toRemoveKeys) {
                    LOG.trace("Removing: header:{}", key);
                    oldExchange.getMessage().removeHeader(key);
                }
            }
        }

        return oldExchange;
    }

    private boolean hasHeaderPatterns() {
        String[] parts = filter.split(",");
        for (String pattern : parts) {
            if (pattern.startsWith("--")) {
                continue;
            }
            if (pattern.startsWith("header:") || pattern.startsWith("+header:") || pattern.startsWith("-header:")) {
                return true;
            }
        }
        return false;
    }

    private boolean isExcludeOnlyHeaderPatterns() {
        String[] parts = filter.split(",");
        for (String pattern : parts) {
            if (pattern.startsWith("--")) {
                continue;
            }
            if (pattern.startsWith("header:") || pattern.startsWith("+header:")) {
                return false;
            }
        }
        return true;
    }

    private boolean hasHeaderPattern(String key) {
        String[] parts = filter.split(",");
        for (String pattern : parts) {
            if (pattern.startsWith("--")) {
                continue;
            }
            String header = null;
            if (pattern.startsWith("header:") || pattern.startsWith("+header:")) {
                header = StringHelper.after(pattern, "header:");
            } else if (pattern.startsWith("-header:")) {
                header = StringHelper.after(pattern, "-header:");
            }
            if (header != null && PatternHelper.matchPattern(key, header)) {
                return true;
            }
        }
        return false;
    }

    private boolean isIncludedHeader(String key) {
        String[] parts = filter.split(",");
        for (String pattern : parts) {
            if (pattern.startsWith("--")) {
                continue;
            }
            if (pattern.startsWith("header:") || pattern.startsWith("+header:")) {
                pattern = StringHelper.after(pattern, "header:");
            }
            if (PatternHelper.matchPattern(key, pattern)) {
                return true;
            }
        }
        return false;
    }

    private boolean isExcludedHeader(String key) {
        String[] parts = filter.split(",");
        for (String pattern : parts) {
            if (pattern.startsWith("--")) {
                continue;
            }
            if (pattern.startsWith("-header:")) {
                pattern = StringHelper.after(pattern, "-header:");
            }
            if (PatternHelper.matchPattern(key, pattern)) {
                return true;
            }
        }
        return false;
    }

    private boolean isBodyEnabled() {
        // body is always enabled unless excluded
        String[] parts = filter.split(",");

        boolean onlyExclude = true;
        for (String pattern : parts) {
            if (pattern.startsWith("--")) {
                continue;
            }
            if ("body".equals(pattern) || "+body".equals(pattern)) {
                return true;
            } else if ("-body".equals(pattern)) {
                return false;
            }
            onlyExclude &= pattern.startsWith("-");
        }
        // body is enabled if we only have exclude patterns
        return onlyExclude;
    }

    private boolean isHeadersEnabled() {
        // headers may be enabled unless excluded
        String[] parts = filter.split(",");

        boolean onlyExclude = true;
        for (String pattern : parts) {
            if (pattern.startsWith("--")) {
                continue;
            }
            // if there is individual header filters then we cannot rely on this
            if (pattern.startsWith("header:") || pattern.startsWith("+header:") || pattern.startsWith("-header:")) {
                return false;
            }
            if ("headers".equals(pattern) || "+headers".equals(pattern)) {
                return true;
            } else if ("-headers".equals(pattern)) {
                return false;
            }
            onlyExclude &= pattern.startsWith("-");
        }
        // headers is enabled if we only have exclude patterns
        return onlyExclude;
    }

}
