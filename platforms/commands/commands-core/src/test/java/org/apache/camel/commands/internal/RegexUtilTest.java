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
package org.apache.camel.commands.internal;

import java.util.regex.Pattern;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class RegexUtilTest {

    @Test
    public void testWildcardAsRegex() throws Exception {
        String testRouteId1 = "route.inbound.systema";
        String testRouteId2 = "route.inbound.systemb";
        String testRouteId3 = "route.outbound.systema";
        String testRouteId4 = "route.outbound.systemb";
        String testRouteId5 = "outbound.systemc";

        assertTrue(Pattern.matches(RegexUtil.wildcardAsRegex("route.inbound*"), testRouteId1));
        assertTrue(!Pattern.matches(RegexUtil.wildcardAsRegex(".inbound*"), testRouteId2));
        assertTrue(Pattern.matches(RegexUtil.wildcardAsRegex("*.inbound*"), testRouteId2));

        assertTrue(Pattern.matches(RegexUtil.wildcardAsRegex("*outbound*"), testRouteId3));
        assertTrue(Pattern.matches(RegexUtil.wildcardAsRegex("*outbound*"), testRouteId4));
        assertTrue(Pattern.matches(RegexUtil.wildcardAsRegex("*outbound*"), testRouteId5));

        assertTrue(Pattern.matches(RegexUtil.wildcardAsRegex("*"), testRouteId1));
        assertTrue(Pattern.matches(RegexUtil.wildcardAsRegex("*"), testRouteId2));
        assertTrue(Pattern.matches(RegexUtil.wildcardAsRegex("*"), testRouteId3));
        assertTrue(Pattern.matches(RegexUtil.wildcardAsRegex("*"), testRouteId4));
        assertTrue(Pattern.matches(RegexUtil.wildcardAsRegex("*"), testRouteId5));

        assertTrue(Pattern.matches(RegexUtil.wildcardAsRegex("route.inbound.systema"), testRouteId1));
    }

}
