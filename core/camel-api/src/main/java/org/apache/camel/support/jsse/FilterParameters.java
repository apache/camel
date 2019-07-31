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
package org.apache.camel.support.jsse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Represents a set of regular expression based filter patterns for
 * including and excluding content of some type.
 */
public class FilterParameters extends JsseParameters {

    protected List<String> include;
    protected List<String> exclude;

    /**
     * Returns a live copy of the list of patterns to include.
     * The list of excludes takes precedence over the include patterns.
     *
     * @return the list of patterns to include
     */
    public List<String> getInclude() {
        if (this.include == null) {
            this.include = new ArrayList<>();
        }
        return this.include;
    }

    /**
     * Returns a live copy of the list of patterns to exclude.
     * This list takes precedence over the include patterns.
     *
     * @return the list of patterns to exclude
     */
    public List<String> getExclude() {
        if (exclude == null) {
            exclude = new ArrayList<>();
        }
        return this.exclude;
    }
    
    /**
     * Returns a list of compiled {@code Pattern}s based on the
     * values of the include list.
     *
     * @return the list of compiled expressions, never {@code null}
     *
     * @throws PatternSyntaxException if any of the expressions are invalid
     */
    public List<Pattern> getIncludePatterns() {
        return this.getPattern(this.getInclude());
    }
    
    /**
     * Returns a list of compiled {@code Pattern}s based on the
     * values of the exclude list.
     *
     * @return the list of compiled expressions, never {@code null}
     * 
     * @throws PatternSyntaxException if any of the expressions are invalid
     */
    public List<Pattern> getExcludePatterns() {
        return this.getPattern(this.getExclude());
    }
    
    /**
     * Returns an immutable collection of compiled filter patterns based on
     * the state of this instance at the time of invocation.
     */
    public Patterns getPatterns() {
        return new Patterns(this.getIncludePatterns(), this.getExcludePatterns());
    }
    
    /**
     * Compiles {@code Pattern}s for each expression in {@code patternStrings}.
     *
     * @param patternStrings the list of regular expressions to compile
     *
     * @return the list of compiled patterns
     *
     * @throws PatternSyntaxException if any of the expressions are invalid
     */
    protected List<Pattern> getPattern(List<String> patternStrings) {
        List<Pattern> patterns = new ArrayList<>(patternStrings.size());
        
        for (String expression : patternStrings) {
            patterns.add(Pattern.compile(this.parsePropertyValue(expression)));
        }
        return patterns;
    }
    
    /**
     * An immutable collection of compiled includes and excludes filter {@link Pattern}s.
     */
    public static class Patterns {
        private final List<Pattern> includes;
        private final List<Pattern> excludes;
        
        public Patterns(List<Pattern> includes, List<Pattern> excludes) {
            this.includes = Collections.unmodifiableList(new ArrayList<>(includes));
            this.excludes = Collections.unmodifiableList(new ArrayList<>(excludes));
        }

        public List<Pattern> getIncludes() {
            return includes;
        }

        public List<Pattern> getExcludes() {
            return excludes;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("Patterns [includes=");
            builder.append(includes);
            builder.append(", excludes=");
            builder.append(excludes);
            builder.append("]");
            return builder.toString();
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("FilterParameters[include=");
        builder.append(Arrays.toString(getInclude().toArray(new String[getInclude().size()])));
        builder.append(", exclude=");
        builder.append(Arrays.toString(getExclude().toArray(new String[getExclude().size()])));
        builder.append("]");
        return builder.toString();
    }
}
