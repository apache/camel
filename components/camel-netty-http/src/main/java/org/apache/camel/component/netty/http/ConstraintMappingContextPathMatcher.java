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
package org.apache.camel.component.netty.http;

import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.camel.util.EndpointHelper;

/**
 * A {@link ContextPathMatcher} which can be used to define a set of mappings to
 * as constraints.
 * <p/>
 * This matcher will match as <tt>true</tt> if no inclusions has been defined.
 * First all the inclusions is check for matching. If a inclusion matches,
 * then the exclusion is checked, and if any of them matches, then the exclusion
 * will override the match and force returning <tt>false</tt>.
 * <p/>
 * Wildcards and regular expressions is supported as this implementation uses
 * {@link EndpointHelper#matchPattern(String, String)} method for matching.
 * <p/>
 * This constraint matcher allows you to setup context path rules that will restrict
 * access to paths, and then override and have exclusions that may allow access to
 * public paths.
 */
public class ConstraintMappingContextPathMatcher implements ContextPathMatcher {

    private Set<String> inclusions;
    private Set<String> exclusions;

    @Override
    public boolean matches(String target) {
        boolean matches = true;

        if (inclusions != null && !inclusions.isEmpty()) {
            boolean found = false;
            for (String constraint : inclusions) {
                if (EndpointHelper.matchPattern(target, constraint)) {
                    found = true;
                    break;
                }
            }
            matches = found;
        }

        // if matches check for any exclusions
        if (matches && exclusions != null && !exclusions.isEmpty()) {
            for (String constraint : exclusions) {
                if (EndpointHelper.matchPattern(target, constraint)) {
                    // force false if this was an exclusion
                    matches = false;
                    break;
                }
            }
        }

        return matches;
    }

    public void addInclusion(String constraint) {
        if (inclusions == null) {
            inclusions = new LinkedHashSet<String>();
        }
        inclusions.add(constraint);
    }

    public void addExclusion(String constraint) {
        if (exclusions == null) {
            exclusions = new LinkedHashSet<String>();
        }
        exclusions.add(constraint);
    }

    public Set<String> getInclusions() {
        return inclusions;
    }

    public void setInclusions(Set<String> inclusions) {
        this.inclusions = inclusions;
    }

    public Set<String> getExclusions() {
        return exclusions;
    }

    public void setExclusions(Set<String> exclusions) {
        this.exclusions = exclusions;
    }
}
