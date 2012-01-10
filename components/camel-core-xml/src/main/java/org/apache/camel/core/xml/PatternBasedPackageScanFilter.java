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
package org.apache.camel.core.xml;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.camel.spi.PackageScanFilter;
import org.apache.camel.util.AntPathMatcher;

/**
 * {@link PatternBasedPackageScanFilter} uses an underlying
 * {@link AntPathMatcher} to filter scanned files according to include and
 * exclude patterns.
 * 
 * @see AntPathMatcher
 */
public class PatternBasedPackageScanFilter implements PackageScanFilter {
    private final AntPathMatcher matcher = new AntPathMatcher();

    private List<String> excludePatterns;
    private List<String> includePatterns;

    /**
     * add and exclude pattern to the filter. Classes matching this pattern will
     * not match the filter 
     */
    public void addExcludePattern(String excludePattern) {
        if (excludePatterns == null) {
            excludePatterns = new ArrayList<String>();
        }
        excludePatterns.add(excludePattern);
    }

    /**
     * add and include pattern to the filter. Classes must match one of supplied
     * include patterns to match the filter 
     */
    public void addIncludePattern(String includePattern) {
        if (includePatterns == null) {
            includePatterns = new ArrayList<String>();
        }
        includePatterns.add(includePattern);
    }
    
    public void addIncludePatterns(Collection<String> includes) {
        if (includePatterns == null) {
            includePatterns = new ArrayList<String>();
        }
        includePatterns.addAll(includes);
    }
    
    public void addExcludePatterns(Collection<String> excludes) {
        if (excludePatterns == null) {
            excludePatterns = new ArrayList<String>();
        }
        excludePatterns.addAll(excludes);
    }

    /**
     * Tests if a given class matches the patterns in this filter. Patterns are
     * specified by {@link AntPathMatcher}
     * <p>
     * if no include or exclude patterns are set then all classes match.
     * <p>
     * If the filter contains only include filters, then the candidate class
     * must match one of the include patterns to match the filter and return
     * true.
     * <p>
     * If the filter contains only exclude filters, then the filter will return
     * true unless the candidate class matches an exclude pattern.
     * <p>
     * if this contains both include and exclude filters, then the above rules
     * apply with excludes taking precedence over includes i.e. an include
     * pattern of java.util.* and an exclude pattern of java.util.jar.* will
     * include a file only if it is in the util pkg and not in the util.jar
     * package.
     * 
     * @return true if candidate class matches according to the above rules
     */
    public boolean matches(Class<?> candidateClass) {
        String candidate = candidateClass.getName();
        if (includePatterns != null || excludePatterns != null) {

            if (excludePatterns != null && excludePatterns.size() > 0) {
                if (matchesAny(excludePatterns, candidate)) {
                    return false;
                }
            }

            if (includePatterns != null && includePatterns.size() > 0) {
                return matchesAny(includePatterns, candidate);
            }

        }
        return true;
    }

    private boolean matchesAny(List<String> patterns, String candidate) {
        for (String pattern : patterns) {
            if (matcher.match(pattern, candidate)) {
                return true;
            }
        }
        return false;
    }

}
