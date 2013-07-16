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
import java.util.Map;
import java.util.Set;

import org.apache.camel.util.EndpointHelper;

/**
 * A default {@link SecurityConstraint} which can be used to define a set of mappings to
 * as constraints.
 * <p/>
 * This constraint will match as <tt>true</tt> if no inclusions has been defined.
 * First all the inclusions is check for matching. If a inclusion matches,
 * then the exclusion is checked, and if any of them matches, then the exclusion
 * will override the match and force returning <tt>false</tt>.
 * <p/>
 * Wildcards and regular expressions is supported as this implementation uses
 * {@link EndpointHelper#matchPattern(String, String)} method for matching.
 * <p/>
 * This restricted constraint allows you to setup context path rules that will restrict
 * access to paths, and then override and have exclusions that may allow access to
 * public paths.
 */
public class SecurityConstraintMapping implements SecurityConstraint {

    // url -> roles
    private Map<String, String> inclusions;
    // url
    private Set<String> exclusions;

    @Override
    public String restricted(String url) {
        // check excluded first
        if (excludedUrl(url)) {
            return null;
        }

        // is the url restricted?
        String constraint = includedUrl(url);
        if (constraint == null) {
            return null;
        }

        // is there any roles for the restricted url?
        String roles = inclusions != null ? inclusions.get(constraint) : null;
        if (roles == null) {
            // use wildcard to indicate any role is accepted
            return "*";
        } else {
            return roles;
        }
    }

    private String includedUrl(String url) {
        String candidate = null;
        if (inclusions != null && !inclusions.isEmpty()) {
            for (String constraint : inclusions.keySet()) {
                if (EndpointHelper.matchPattern(url, constraint)) {
                    if (candidate == null) {
                        candidate = constraint;
                    } else if (constraint.length() > candidate.length()) {
                        // we want the constraint that has the longest context-path matching as its
                        // the most explicit for the target url
                        candidate = constraint;
                    }
                }
            }
            return candidate;
        }

        // by default if no included has been configured then everything is restricted
        return "*";
    }

    private boolean excludedUrl(String url) {
        if (exclusions != null && !exclusions.isEmpty()) {
            for (String constraint : exclusions) {
                if (EndpointHelper.matchPattern(url, constraint)) {
                    // force not matches if this was an exclusion
                    return true;
                }
            }
        }

        return false;
    }

    public void addInclusion(String constraint) {
        if (inclusions == null) {
            inclusions = new java.util.LinkedHashMap<String, String>();
        }
        inclusions.put(constraint, null);
    }

    public void addInclusion(String constraint, String roles) {
        if (inclusions == null) {
            inclusions = new java.util.LinkedHashMap<String, String>();
        }
        inclusions.put(constraint, roles);
    }

    public void addExclusion(String constraint) {
        if (exclusions == null) {
            exclusions = new LinkedHashSet<String>();
        }
        exclusions.add(constraint);
    }

    public void setInclusions(Map<String, String> inclusions) {
        this.inclusions = inclusions;
    }

    public void setExclusions(Set<String> exclusions) {
        this.exclusions = exclusions;
    }
}
