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
package org.apache.camel.support;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.camel.Exchange;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.Metadata;

/**
 * The default header filtering strategy. Users can configure filter by setting filter set and/or setting a regular
 * expression. Subclass can add extended filter logic in
 * {@link #extendedFilter(org.apache.camel.spi.HeaderFilterStrategy.Direction, String, Object, org.apache.camel.Exchange)}
 * <p>
 * Filters are associated with directions (in or out). "In" direction is referred to propagating headers "to" Camel
 * message. The "out" direction is opposite which is referred to propagating headers from Camel message to a native
 * message like JMS and CXF message. You can see example of DefaultHeaderFilterStrategy are being extended and invoked
 * in camel-jms and camel-cxf components.
 */
@Metadata(label = "bean",
          description = "The default header filtering strategy. Users can configure which headers is allowed or denied.",
          annotations = { "interfaceName=org.apache.camel.spi.HeaderFilterStrategy" })
@Configurer(metadataOnly = true)
public class DefaultHeaderFilterStrategy implements HeaderFilterStrategy {

    /**
     * A filter pattern that only accepts keys starting with <tt>Camel</tt> or <tt>org.apache.camel.</tt>
     *
     * @deprecated use {@link #CAMEL_FILTER_STARTS_WITH}
     */
    @Deprecated(since = "3.9.0")
    public static final Pattern CAMEL_FILTER_PATTERN = Pattern.compile("(?i)(Camel|org\\.apache\\.camel)[.a-zA-z0-9]*");

    /**
     * A filter pattern for keys starting with <tt>Camel</tt>, <tt>camel</tt>, or <tt>org.apache.camel.</tt>
     */
    public static final String[] CAMEL_FILTER_STARTS_WITH = new String[] { "Camel", "camel", "org.apache.camel." };

    @Metadata(javaType = "java.lang.String",
              description = "Sets the in direction filter set. The in direction is referred to copying headers from an external message to a Camel message."
                            + " Multiple patterns can be separated by comma")
    private Set<String> inFilter;
    private Pattern inFilterPattern;
    private String[] inFilterStartsWith;

    @Metadata(javaType = "java.lang.String",
              description = "Sets the out direction filter set. The out direction is referred to copying headers from a Camel message to an external message."
                            + " Multiple patterns can be separated by comma")
    private Set<String> outFilter;
    private Pattern outFilterPattern;
    private String[] outFilterStartsWith;

    @Metadata(label = "advanced", defaultValue = "false",
              description = "Whether header names should be converted to lower case before checking it with the filter Set."
                            + " It does not affect filtering using regular expression pattern.")
    private boolean lowerCase;
    @Metadata(label = "advanced", defaultValue = "false",
              description = "Whether to allow null values. By default a header is skipped if its value is null. Setting this to true will preserve the header.")
    private boolean allowNullValues;
    @Metadata(label = "advanced", defaultValue = "false",
              description = "Sets the caseInsensitive property which is a boolean to determine whether header names should be case insensitive"
                            + " when checking it with the filter set. It does not affect filtering using regular expression pattern.")
    private boolean caseInsensitive;
    @Metadata(label = "advanced", defaultValue = "true",
              description = "Sets what to do when a pattern or filter set is matched."
                            + " When set to true, a match will filter out the header. This is the default value for backwards compatibility."
                            + " When set to false, the pattern or filter will indicate that the header must be kept; anything not matched will be filtered (skipped).")
    private boolean filterOnMatch = true; // defaults to the previous behaviour

    @Override
    public boolean applyFilterToCamelHeaders(String headerName, Object headerValue, Exchange exchange) {
        return doFiltering(Direction.OUT, headerName, headerValue, exchange);
    }

    @Override
    public boolean applyFilterToExternalHeaders(String headerName, Object headerValue, Exchange exchange) {
        return doFiltering(Direction.IN, headerName, headerValue, exchange);
    }

    /**
     * Gets the "out" direction filter set. The "out" direction is referred to copying headers from a Camel message to
     * an external message.
     *
     * @return a set that contains header names that should be excluded.
     */
    public Set<String> getOutFilter() {
        if (outFilter == null) {
            outFilter = new HashSet<>();
        }

        return outFilter;
    }

    /**
     * Sets the "out" direction filter set. The "out" direction is referred to copying headers from a Camel message to
     * an external message.
     *
     * @param value the filter
     */
    public void setOutFilter(Set<String> value) {
        outFilter = value;
    }

    public void setOutFilter(String value) {
        if (value != null) {
            this.outFilter = Set.of(value.split(","));
        }
    }

    /**
     * Sets the "out" direction filter by starts with pattern. The "out" direction is referred to copying headers from a
     * Camel message to an external message.
     *
     * @param outFilterStartsWith one or more key names to use for filtering using starts with
     */
    public void setOutFilterStartsWith(String... outFilterStartsWith) {
        this.outFilterStartsWith = outFilterStartsWith;
    }

    /**
     * Gets the "out" direction filter regular expression {@link Pattern}. The "out" direction is referred to copying
     * headers from Camel message to an external message. If the pattern matches a header, the header will be filtered
     * (skipped).
     *
     * @return regular expression filter pattern
     */
    public String getOutFilterPattern() {
        return outFilterPattern == null ? null : outFilterPattern.pattern();
    }

    /**
     * Sets the "out" direction filter regular expression {@link Pattern}. The "out" direction is referred to copying
     * headers from Camel message to an external message. If the pattern matches a header, the header will be filtered
     * (skipped).
     *
     * @param value regular expression filter pattern
     */
    public void setOutFilterPattern(String value) {
        if (value == null) {
            outFilterPattern = null;
        } else {
            outFilterPattern = Pattern.compile(value);
        }
    }

    /**
     * Sets the "out" direction filter regular expression {@link Pattern}. The "out" direction is referred to copying
     * headers from Camel message to an external message. If the pattern matches a header, the header will be filtered
     * (skipped).
     *
     * @param pattern regular expression filter pattern
     */
    public void setOutFilterPattern(Pattern pattern) {
        outFilterPattern = pattern;
    }

    /**
     * Gets the "in" direction filter set. The "in" direction is referred to copying headers from an external message to
     * a Camel message.
     *
     * @return a set that contains header names that should be excluded.
     */
    public Set<String> getInFilter() {
        if (inFilter == null) {
            inFilter = new HashSet<>();
        }
        return inFilter;
    }

    /**
     * Sets the "in" direction filter set. The "in" direction is referred to copying headers from an external message to
     * a Camel message.
     *
     * @param value the filter
     */
    public void setInFilter(Set<String> value) {
        inFilter = value;
    }

    public void setInFilter(String value) {
        if (value != null) {
            this.inFilter = Set.of(value.split(","));
        }
    }

    /**
     * Sets the "in" direction filter by starts with pattern. The "in" direction is referred to copying headers from an
     * external message to a Camel message.
     *
     * @param inFilterStartsWith one or more key names to use for filtering using starts with
     */
    public void setInFilterStartsWith(String... inFilterStartsWith) {
        this.inFilterStartsWith = inFilterStartsWith;
    }

    /**
     * Gets the "in" direction filter regular expression {@link Pattern}. The "in" direction is referred to copying
     * headers from an external message to a Camel message. If the pattern matches a header, the header will be filtered
     * (skipped).
     *
     * @return regular expression filter pattern
     */
    public String getInFilterPattern() {
        return inFilterPattern == null ? null : inFilterPattern.pattern();
    }

    /**
     * Sets the "in" direction filter regular expression {@link Pattern}. The "in" direction is referred to copying
     * headers from an external message to a Camel message. If the pattern matches a header, the header will be filtered
     * (skipped).
     *
     * @param value regular expression filter pattern
     */
    public void setInFilterPattern(String value) {
        if (value == null) {
            inFilterPattern = null;
        } else {
            inFilterPattern = Pattern.compile(value);
        }
    }

    /**
     * Sets the "in" direction filter regular expression {@link Pattern}. The "in" direction is referred to copying
     * headers from an external message to a Camel message. If the pattern matches a header, the header will be filtered
     * (skipped).
     *
     * @param pattern regular expression filter pattern
     */
    public void setInFilterPattern(Pattern pattern) {
        inFilterPattern = pattern;
    }

    /**
     * Gets the isLowercase property which is a boolean to determine whether header names should be converted to lower
     * case before checking it with the filter Set. It does not affect filtering using regular expression pattern.
     */
    public boolean isLowerCase() {
        return lowerCase;
    }

    /**
     * Sets the isLowercase property which is a boolean to determine whether header names should be converted to lower
     * case before checking it with the filter Set. It does not affect filtering using regular expression pattern.
     */
    public void setLowerCase(boolean value) {
        lowerCase = value;
    }

    /**
     * Gets the caseInsensitive property which is a boolean to determine whether header names should be case insensitive
     * when checking it with the filter set. It does not affect filtering using regular expression pattern.
     *
     * @return <tt>true</tt> if header names is case insensitive.
     */
    public boolean isCaseInsensitive() {
        return caseInsensitive;
    }

    /**
     * Sets the caseInsensitive property which is a boolean to determine whether header names should be case insensitive
     * when checking it with the filter set. It does not affect filtering using regular expression pattern,
     *
     * @param caseInsensitive <tt>true</tt> if header names is case insensitive.
     */
    public void setCaseInsensitive(boolean caseInsensitive) {
        this.caseInsensitive = caseInsensitive;
    }

    public boolean isAllowNullValues() {
        return allowNullValues;
    }

    /**
     * Whether to allow null values.
     * <p>
     * By default a header is skipped if its value is null. Setting this to true will preserve the header.
     */
    public void setAllowNullValues(boolean value) {
        allowNullValues = value;
    }

    public boolean isFilterOnMatch() {
        return filterOnMatch;
    }

    /**
     * Sets the filterOnMatch property which is a boolean to determine what to do when a pattern or filter set is
     * matched.
     * <p>
     * When set to true, a match will filter out the header. This is the default value for backwards compatibility.
     * <p>
     * When set to false, the pattern or filter will indicate that the header must be kept; anything not matched will be
     * filtered (skipped).
     *
     * @param filterOnMatch <tt>true</tt> if a match filters (skips) the header.
     */
    public void setFilterOnMatch(boolean filterOnMatch) {
        this.filterOnMatch = filterOnMatch;
    }

    protected boolean extendedFilter(Direction direction, String key, Object value, Exchange exchange) {
        return !filterOnMatch;
    }

    private boolean doFiltering(Direction direction, String headerName, Object headerValue, Exchange exchange) {
        if (headerName == null) {
            return true;
        }

        if (headerValue == null && !allowNullValues) {
            return true;
        }

        Pattern pattern = null;
        Set<String> filter = null;
        String[] startsWith = null;

        if (Direction.OUT == direction) {
            pattern = outFilterPattern;
            filter = outFilter;
            startsWith = outFilterStartsWith;
        } else if (Direction.IN == direction) {
            pattern = inFilterPattern;
            filter = inFilter;
            startsWith = inFilterStartsWith;
        }

        String lower = null;

        if (startsWith != null) {
            if (tryHeaderMatch(headerName, startsWith)) {
                return filterOnMatch;
            }
            if (lowerCase) {
                lower = headerName.toLowerCase();
                if (tryHeaderMatch(lower, startsWith)) {
                    return filterOnMatch;
                }
            }
        }

        if (pattern != null) {
            if (tryPattern(headerName, lower, pattern)) {
                return filterOnMatch;
            }
        }

        if (filter != null) {
            if (evalFilterMatch(headerName, lower, filter)) {
                return filterOnMatch;
            }
        }

        return extendedFilter(direction, headerName, headerValue, exchange);
    }

    private boolean tryPattern(String headerName, String lower, Pattern pattern) {
        // optimize if its the default pattern as we know the pattern is to check for keys starting with Camel
        if (pattern == CAMEL_FILTER_PATTERN) {
            boolean match = headerName.startsWith("Camel") || headerName.startsWith("camel")
                    || headerName.startsWith("org.apache.camel.");
            if (match) {
                return true;
            }
            if (lowerCase) {
                if (lower == null) {
                    lower = headerName.toLowerCase();
                }
                match = lower.startsWith("camel") || lower.startsWith("org.apache.camel.");
                if (match) {
                    return true;
                }
            }
        } else if (pattern.matcher(headerName).matches()) {
            return true;
        }
        return false;
    }

    private boolean tryHeaderMatch(String headerName, String[] startsWith) {
        for (String s : startsWith) {
            boolean match = headerName.startsWith(s);
            if (match) {
                return true;
            }
        }
        return false;
    }

    private boolean evalFilterMatch(String headerName, String lower, Set<String> filter) {
        if (isCaseInsensitive()) {
            for (String filterString : filter) {
                if (filterString.equalsIgnoreCase(headerName)) {
                    return true;
                }
            }
        } else if (lowerCase) {
            if (lower == null) {
                lower = headerName.toLowerCase();
            }
            if (filter.contains(lower)) {
                return true;
            }
        } else {
            if (filter.contains(headerName)) {
                return true;
            }
        }
        return false;
    }

}
