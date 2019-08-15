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
package org.apache.camel.component.netty.http.rest;

import org.apache.camel.component.netty.http.RestContextPathMatcher;
import org.junit.Assert;
import org.junit.Test;

public class RestPathMatchingTest extends Assert {

    private RestContextPathMatcher matcher = new RestContextPathMatcher("", "", null, true);

    @Test
    public void testRestPathMatcher() throws Exception {
        assertTrue(matcher.matchRestPath("/foo/", "/foo/", true));
        assertTrue(matcher.matchRestPath("/foo/", "foo/", true));
        assertTrue(matcher.matchRestPath("/foo/", "foo", true));
        assertTrue(matcher.matchRestPath("foo/", "foo", true));
        assertTrue(matcher.matchRestPath("foo", "foo", true));
        assertTrue(matcher.matchRestPath("foo/", "foo", true));
        assertTrue(matcher.matchRestPath("/foo/", "foo", true));

        assertTrue(matcher.matchRestPath("/foo/1234/list/2014", "/foo/1234/list/2014", true));
        assertTrue(matcher.matchRestPath("/foo/1234/list/2014/", "/foo/1234/list/2014", true));
        assertTrue(matcher.matchRestPath("/foo/1234/list/2014", "/foo/1234/list/2014/", true));
        assertTrue(matcher.matchRestPath("/foo/1234/list/2014/", "/foo/1234/list/2014/", true));
        assertTrue(matcher.matchRestPath("/foo/1234/list/2014", "/foo/{user}/list/{year}", true));

        assertFalse(matcher.matchRestPath("/foo/", "/bar/", true));
        assertFalse(matcher.matchRestPath("/foo/1234/list/2014", "/foo/1234/list/2015", true));
        assertFalse(matcher.matchRestPath("/foo/1234/list/2014/", "/foo/1234/list/2015", true));
        assertFalse(matcher.matchRestPath("/foo/1234/list/2014", "/foo/1234/list/2015/", true));
        assertFalse(matcher.matchRestPath("/foo/1234/list/2014/", "/foo/1234/list/2015/", true));
        assertFalse(matcher.matchRestPath("/foo/1234/list/2014", "/foo/{user}/list/", true));

        assertTrue(matcher.matchRestPath("/foo/1/list/2", "/foo/{user}/list/{year}", true));
        assertTrue(matcher.matchRestPath("/foo/1234567890/list/2", "/foo/{user}/list/{year}", true));
        assertTrue(matcher.matchRestPath("/foo/1234567890/list/1234567890", "/foo/{user}/list/{year}", true));

        assertTrue(matcher.matchRestPath("/123/list/2014", "/{user}/list/{year}", true));
        assertTrue(matcher.matchRestPath("/1234567890/list/2014", "/{user}/list/{year}", true));
    }

    @Test
    public void testRestPathMatcherNoWildcard() throws Exception {
        assertTrue(matcher.matchRestPath("/foo/", "/foo/", false));
        assertTrue(matcher.matchRestPath("/foo/", "foo/", false));
        assertTrue(matcher.matchRestPath("/foo/", "foo", false));
        assertTrue(matcher.matchRestPath("foo/", "foo", false));
        assertTrue(matcher.matchRestPath("foo", "foo", false));
        assertTrue(matcher.matchRestPath("foo/", "foo", false));
        assertTrue(matcher.matchRestPath("/foo/", "foo", false));

        assertTrue(matcher.matchRestPath("/foo/1234/list/2014", "/foo/1234/list/2014", false));
        assertTrue(matcher.matchRestPath("/foo/1234/list/2014/", "/foo/1234/list/2014", false));
        assertTrue(matcher.matchRestPath("/foo/1234/list/2014", "/foo/1234/list/2014/", false));
        assertTrue(matcher.matchRestPath("/foo/1234/list/2014/", "/foo/1234/list/2014/", false));
        assertTrue(matcher.matchRestPath("/foo/1234/list/2014", "/foo/{user}/list/{year}", true));

        assertFalse(matcher.matchRestPath("/foo/", "/bar/", false));
        assertFalse(matcher.matchRestPath("/foo/1234/list/2014", "/foo/1234/list/2015", false));
        assertFalse(matcher.matchRestPath("/foo/1234/list/2014/", "/foo/1234/list/2015", false));
        assertFalse(matcher.matchRestPath("/foo/1234/list/2014", "/foo/1234/list/2015/", false));
        assertFalse(matcher.matchRestPath("/foo/1234/list/2014/", "/foo/1234/list/2015/", false));
        assertFalse(matcher.matchRestPath("/foo/1234/list/2014", "/foo/{user}/list/", false));

        assertFalse(matcher.matchRestPath("/foo/1/list/2", "/foo/{user}/list/{year}", false));
        assertFalse(matcher.matchRestPath("/foo/1234567890/list/2", "/foo/{user}/list/{year}", false));
        assertFalse(matcher.matchRestPath("/foo/1234567890/list/1234567890", "/foo/{user}/list/{year}", false));

        assertFalse(matcher.matchRestPath("/123/list/2014", "/{user}/list/{year}", false));
        assertFalse(matcher.matchRestPath("/1234567890/list/2014", "/{user}/list/{year}", false));
    }

}
