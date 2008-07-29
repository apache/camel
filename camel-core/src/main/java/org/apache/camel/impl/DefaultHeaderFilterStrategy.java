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
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.camel.spi.HeaderFilterStrategy;

/**
 * The default header filtering strategy.  Users can configure filter by 
 * setting filter set and/or setting a regular expression.  Subclass can 
 * add extended filter logic in 
 * {@link #extendedFilter(org.apache.camel.impl.DefaultHeaderFilterStrategy.Direction, String, Object)}
 * 
 * Filters are associated with directions (in or out).  "In" direction is referred
 * to propagating headers "to" Camel message.
 *
 * @version $Revision$
 */
public class DefaultHeaderFilterStrategy implements HeaderFilterStrategy {
    
    protected enum Direction { IN, OUT };
    
    private Set<String> inFilter;
    private Pattern inFilterPattern;

    private Set<String> outFilter;
    private Pattern outFilterPattern;

    private boolean isLowercase;
    private boolean allowNullValues;

    private boolean doFiltering(Direction direction, String headerName, Object headerValue) {
        
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
            if (isLowercase) {
                if (filter.contains(headerName.toLowerCase())) {
                    return true;
                }
            } else {
                if (filter.contains(headerName)) {
                    return true;
                }
            }
        }
            
        if (extendedFilter(direction, headerName, headerValue)) {
            return true;
        }
            
        return false;
    }
    
    protected boolean extendedFilter(Direction direction, String key, Object value) {
        return false;
    }

    public Set<String> getOutFilter() {
        if (outFilter == null) {
            outFilter = new HashSet<String>();
        }
        
        return outFilter;
    }

    public void setOutFilter(Set<String> value) {
        outFilter = value;
    }

    public String getOutFilterPattern() {
        return outFilterPattern == null ? null : outFilterPattern.pattern();
    }
    
    public void setOutFilterPattern(String value) {
        if (value == null) {
            outFilterPattern = null;
        } else {
            outFilterPattern = Pattern.compile(value);
        }
    }
    
    public Set<String> getInFilter() {
        if (inFilter == null) {
            inFilter = new HashSet<String>();
        }
        return inFilter;
    }

    public void setInFilter(Set<String> value) {
        inFilter = value;
    }

    public String getInFilterPattern() {
        return inFilterPattern == null ? null : inFilterPattern.pattern();
    }
    
    public void setInFilterPattern(String value) {
        if (value == null) {
            inFilterPattern = null;
        } else {
            inFilterPattern = Pattern.compile(value);
        }
    }

    public boolean getIsLowercase() {
        return isLowercase;
    }
    
    public void setIsLowercase(boolean value) {
        isLowercase = value;
    }
    
    public boolean getAllowNullValues() {
        return allowNullValues;
    }
    
    public void setAllowNullValues(boolean value) {
        allowNullValues = value;
    }

    public boolean applyFilterToCamelHeaders(String headerName, Object headerValue) {
        return doFiltering(Direction.OUT, headerName, headerValue);
    }

    public boolean applyFilterToExternalHeaders(String headerName, Object headerValue) {
        return doFiltering(Direction.IN, headerName, headerValue);
    }
   
}
