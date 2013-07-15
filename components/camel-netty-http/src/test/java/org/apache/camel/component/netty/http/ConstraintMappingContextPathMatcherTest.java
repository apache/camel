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

import junit.framework.TestCase;

public class ConstraintMappingContextPathMatcherTest extends TestCase {

    public void testDefault() {
        ConstraintMappingContextPathMatcher matcher = new ConstraintMappingContextPathMatcher();

        assertTrue(matcher.matches("/"));
        assertTrue(matcher.matches("/foo"));
    }

    public void testFoo() {
        ConstraintMappingContextPathMatcher matcher = new ConstraintMappingContextPathMatcher();
        matcher.addInclusion("/foo");

        assertFalse(matcher.matches("/"));
        assertTrue(matcher.matches("/foo"));
        assertFalse(matcher.matches("/foobar"));
        assertFalse(matcher.matches("/foo/bar"));
    }

    public void testFooWildcard() {
        ConstraintMappingContextPathMatcher matcher = new ConstraintMappingContextPathMatcher();
        matcher.addInclusion("/foo*");

        assertFalse(matcher.matches("/"));
        assertTrue(matcher.matches("/foo"));
        assertTrue(matcher.matches("/foobar"));
        assertTrue(matcher.matches("/foo/bar"));
    }

    public void testFooBar() {
        ConstraintMappingContextPathMatcher matcher = new ConstraintMappingContextPathMatcher();
        matcher.addInclusion("/foo");
        matcher.addInclusion("/bar");

        assertFalse(matcher.matches("/"));
        assertTrue(matcher.matches("/foo"));
        assertFalse(matcher.matches("/foobar"));
        assertFalse(matcher.matches("/foo/bar"));

        assertTrue(matcher.matches("/bar"));
        assertFalse(matcher.matches("/barbar"));
        assertFalse(matcher.matches("/bar/bar"));
    }

    public void testFooBarWildcard() {
        ConstraintMappingContextPathMatcher matcher = new ConstraintMappingContextPathMatcher();
        matcher.addInclusion("/foo*");
        matcher.addInclusion("/bar*");

        assertFalse(matcher.matches("/"));
        assertTrue(matcher.matches("/foo"));
        assertTrue(matcher.matches("/foobar"));
        assertTrue(matcher.matches("/foo/bar"));

        assertTrue(matcher.matches("/bar"));
        assertTrue(matcher.matches("/barbar"));
        assertTrue(matcher.matches("/bar/bar"));
    }

    public void testFooExclusion() {
        ConstraintMappingContextPathMatcher matcher = new ConstraintMappingContextPathMatcher();
        matcher.addInclusion("/foo/*");
        matcher.addExclusion("/foo/public/*");

        assertFalse(matcher.matches("/"));
        assertTrue(matcher.matches("/foo"));
        assertTrue(matcher.matches("/foo/bar"));
        assertFalse(matcher.matches("/foo/public"));
        assertFalse(matcher.matches("/foo/public/open"));
    }

    public void testDefaultExclusion() {
        // everything is restricted unless its from the public
        ConstraintMappingContextPathMatcher matcher = new ConstraintMappingContextPathMatcher();
        matcher.addExclusion("/public/*");
        matcher.addExclusion("/index");
        matcher.addExclusion("/index.html");

        assertTrue(matcher.matches("/"));
        assertTrue(matcher.matches("/foo"));
        assertTrue(matcher.matches("/foo/bar"));
        assertFalse(matcher.matches("/public"));
        assertFalse(matcher.matches("/public/open"));
        assertFalse(matcher.matches("/index"));
        assertFalse(matcher.matches("/index.html"));
    }

}
