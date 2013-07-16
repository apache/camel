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

public class SecurityConstraintMappingTest extends TestCase {

    public void testDefault() {
        SecurityConstraintMapping matcher = new SecurityConstraintMapping();

        assertNotNull(matcher.restricted("/"));
        assertNotNull(matcher.restricted("/foo"));
    }

    public void testFoo() {
        SecurityConstraintMapping matcher = new SecurityConstraintMapping();
        matcher.addInclusion("/foo");

        assertNull(matcher.restricted("/"));
        assertNotNull(matcher.restricted("/foo"));
        assertNull(matcher.restricted("/foobar"));
        assertNull(matcher.restricted("/foo/bar"));
    }

    public void testFooWildcard() {
        SecurityConstraintMapping matcher = new SecurityConstraintMapping();
        matcher.addInclusion("/foo*");

        assertNull(matcher.restricted("/"));
        assertNotNull(matcher.restricted("/foo"));
        assertNotNull(matcher.restricted("/foobar"));
        assertNotNull(matcher.restricted("/foo/bar"));
    }

    public void testFooBar() {
        SecurityConstraintMapping matcher = new SecurityConstraintMapping();
        matcher.addInclusion("/foo");
        matcher.addInclusion("/bar");

        assertNull(matcher.restricted("/"));
        assertNotNull(matcher.restricted("/foo"));
        assertNull(matcher.restricted("/foobar"));
        assertNull(matcher.restricted("/foo/bar"));

        assertNotNull(matcher.restricted("/bar"));
        assertNull(matcher.restricted("/barbar"));
        assertNull(matcher.restricted("/bar/bar"));
    }

    public void testFooBarWildcard() {
        SecurityConstraintMapping matcher = new SecurityConstraintMapping();
        matcher.addInclusion("/foo*");
        matcher.addInclusion("/bar*");

        assertNull(matcher.restricted("/"));
        assertNotNull(matcher.restricted("/foo"));
        assertNotNull(matcher.restricted("/foobar"));
        assertNotNull(matcher.restricted("/foo/bar"));

        assertNotNull(matcher.restricted("/bar"));
        assertNotNull(matcher.restricted("/barbar"));
        assertNotNull(matcher.restricted("/bar/bar"));
    }

    public void testFooExclusion() {
        SecurityConstraintMapping matcher = new SecurityConstraintMapping();
        matcher.addInclusion("/foo/*");
        matcher.addExclusion("/foo/public/*");

        assertNull(matcher.restricted("/"));
        assertNotNull(matcher.restricted("/foo"));
        assertNotNull(matcher.restricted("/foo/bar"));
        assertNull(matcher.restricted("/foo/public"));
        assertNull(matcher.restricted("/foo/public/open"));
    }

    public void testDefaultExclusion() {
        // everything is restricted unless its from the public
        SecurityConstraintMapping matcher = new SecurityConstraintMapping();
        matcher.addExclusion("/public/*");
        matcher.addExclusion("/index");
        matcher.addExclusion("/index.html");

        assertNotNull(matcher.restricted("/"));
        assertNotNull(matcher.restricted("/foo"));
        assertNotNull(matcher.restricted("/foo/bar"));
        assertNull(matcher.restricted("/public"));
        assertNull(matcher.restricted("/public/open"));
        assertNull(matcher.restricted("/index"));
        assertNull(matcher.restricted("/index.html"));
    }

}
