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
package org.apache.camel.impl;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.camel.Exchange;
import org.apache.camel.spi.HeaderFilterStrategy;

/**
 * The default header filtering strategy. Users can configure filter by
 * setting filter set and/or setting a regular expression. Subclass can
 * add extended filter logic in 
 * {@link #extendedFilter(org.apache.camel.spi.HeaderFilterStrategy.Direction, String, Object, org.apache.camel.Exchange)}
 * 
 * Filters are associated with directions (in or out). "In" direction is
 * referred to propagating headers "to" Camel message. The "out" direction
 * is opposite which is referred to propagating headers from Camel message
 * to a native message like JMS and CXF message. You can see example of
 * DefaultHeaderFilterStrategy are being extended and invoked in camel-jms 
 * and camel-cxf components.
 *
 * @version 
 */
public class DefaultHeaderFilterStrategy implements HeaderFilterStrategy {
    
    private Set<String> inFilter;
    private Pattern inFilterPattern;

    private Set<String> outFilter;
    private Pattern outFilterPattern;

    private boolean lowerCase;
    private boolean allowNullValues;
    private boolean caseInsensitive;
    
    public boolean applyFilterToCamelHeaders(String headerName, Object headerValue, Exchange exchange) {
        return doFiltering(Direction.OUT, headerName, headerValue, exchange);
    }

    public boolean applyFilterToExternalHeaders(String headerName, Object headerValue, Exchange exchange) {
        return doFiltering(Direction.IN, headerName, headerValue, exchange);
    }

    /**
     * Gets the "out" direction filter set. The "out" direction is referred to
     * copying headers from a Camel message to an external message.
     * 
     * @return a set that contains header names that should be excluded.
     */
    public Set<String> getOutFilter() {
        if (outFilter == null) {
            outFilter = new HashSet<String>();
        }
        
        return outFilter;
    }

    /**
     * Sets the "out" direction filter set. The "out" direction is referred to
     * copying headers from a Camel message to an external message.
     *
     * @param value  the filter
     */
    public void setOutFilter(Set<String> value) {
        outFilter = value;
    }

    /**
     * Gets the "out" direction filter regular expression {@link Pattern}. The
     * "out" direction is referred to copying headers from Camel message to
     * an external message. If the pattern matches a header, the header will
     * be filtered out. 
     * 
     * @return regular expression filter pattern
     */
    public String getOutFilterPattern() {
        return outFilterPattern == null ? null : outFilterPattern.pattern();
    }

    /**
     * Sets the "out" direction filter regular expression {@link Pattern}. The
     * "out" direction is referred to copying headers from Camel message to
     * an external message. If the pattern matches a header, the header will
     * be filtered out. 
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
     * Gets the "in" direction filter set. The "in" direction is referred to
     * copying headers from an external message to a Camel message.
     * 
     * @return a set that contains header names that should be excluded.
     */
    public Set<String> getInFilter() {
        if (inFilter == null) {
            inFilter = new HashSet<String>();
        }
        return inFilter;
    }

    /**
     * Sets the "in" direction filter set. The "in" direction is referred to
     * copying headers from an external message to a Camel message.
     *
     * @param value the filter
     */
    public void setInFilter(Set<String> value) {
        inFilter = value;
    }

    /**
     * Gets the "in" direction filter regular expression {@link Pattern}. The
     * "in" direction is referred to copying headers from an external message
     * to a Camel message. If the pattern matches a header, the header will
     * be filtered out. 
     * 
     * @return regular expression filter pattern
     */
    public String getInFilterPattern() {
        return inFilterPattern == null ? null : inFilterPattern.pattern();
    }
    
    /**
     * Sets the "in" direction filter regular expression {@link Pattern}. The
     * "in" direction is referred to copying headers from an external message
     * to a Camel message. If the pattern matches a header, the header will
     * be filtered out. 
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
     * Gets the isLowercase property which is a boolean to determine
     * whether header names should be converted to lower case before
     * checking it with the filter Set. It does not affect filtering using
     * regular expression pattern.
     */
    public boolean isLowerCase() {
        return lowerCase;
    }
    
    /**
     * Sets the isLowercase property which is a boolean to determine
     * whether header names should be converted to lower case before
     * checking it with the filter Set. It does not affect filtering using
     * regular expression pattern.
     */
    public void setLowerCase(boolean value) {
        lowerCase = value;
    }

    /**
     * Gets the caseInsensitive property which is a boolean to determine
     * whether header names should be case insensitive when checking it 
     * with the filter set.
     * It does not affect filtering using regular expression pattern.
     * 
     * @return <tt>true</tt> if header names is case insensitive. 
     */
    public boolean isCaseInsensitive() {
        return caseInsensitive;
    }

    /**
     * Sets the caseInsensitive property which is a boolean to determine
     * whether header names should be case insensitive when checking it 
     * with the filter set.
     * It does not affect filtering using regular expression pattern,
     * 
     * @param caseInsensitive <tt>true</tt> if header names is case insensitive.
     */
    public void setCaseInsensitive(boolean caseInsensitive) {
        this.caseInsensitive = caseInsensitive;
    }
    
    public boolean isAllowNullValues() {
        return allowNullValues;
    }
    
    public void setAllowNullValues(boolean value) {
        allowNullValues = value;
    }   

    protected boolean extendedFilter(Direction direction, String key, Object value, Exchange exchange) {
        return false;
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
        
        if (Direction.OUT == direction) {
            pattern = outFilterPattern;
            filter = outFilter;                
        } else if (Direction.IN == direction) {
            pattern = inFilterPattern;
            filter = inFilter;
        }
   
        if (pattern != null && pattern.matcher(headerName).matches()) {
            return true;
        }
            
        if (filter != null) {
            if (isCaseInsensitive()) {
                for (String filterString : filter) {
                    if (filterString.equalsIgnoreCase(headerName)) {
                        return true;
                    }
                }
            } else if (isLowerCase()) {
                if (filter.contains(headerName.toLowerCase(Locale.ENGLISH))) {
                    return true;
                }
            } else {
                if (filter.contains(headerName)) {
                    return true;
                }
            }
        }

        return extendedFilter(direction, headerName, headerValue, exchange);
    }

}
