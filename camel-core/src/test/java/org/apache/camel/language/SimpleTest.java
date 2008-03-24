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
package org.apache.camel.language;

import org.apache.camel.LanguageTestSupport;

/**
 * @version $Revision$
 */
public class SimpleTest extends LanguageTestSupport {

    public void testSimpleExpressions() throws Exception {
        assertExpression("body", "<hello id='m123'>world!</hello>");
        assertExpression("in.body", "<hello id='m123'>world!</hello>");
        assertExpression("in.header.foo", "abc");
        assertExpression("header.foo", "abc");
    }

    public void testComplexExpressions() throws Exception {
        assertExpression("hey ${in.header.foo}", "hey abc");
        assertExpression("hey ${in.header.foo}!", "hey abc!");
        assertExpression("hey ${in.header.foo}-${in.header.foo}!", "hey abc-abc!");
        assertExpression("hey ${in.header.foo}${in.header.foo}", "hey abcabc");
        assertExpression("${in.header.foo}${in.header.foo}", "abcabc");
        assertExpression("${in.header.foo}", "abc");
        assertExpression("${in.header.foo}!", "abc!");
    }


    public void testInvalidComplexExpression() throws Exception {
        try {
            assertExpression("hey ${foo", "bad expression!");
            fail("Should have thrown an exception!");
        } catch (IllegalArgumentException e) {
            log.debug("Caught expected exception: " + e, e);
        }
    }

    public void testPredicates() throws Exception {
        assertPredicate("body");
        assertPredicate("header.foo");
        assertPredicate("header.madeUpHeader", false);
    }

    protected String getLanguageName() {
        return "simple";
    }
}