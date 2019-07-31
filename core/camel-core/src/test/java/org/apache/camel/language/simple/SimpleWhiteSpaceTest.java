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
package org.apache.camel.language.simple;

import org.apache.camel.LanguageTestSupport;
import org.junit.Test;

public class SimpleWhiteSpaceTest extends LanguageTestSupport {

    @Override
    protected String getLanguageName() {
        return "simple";
    }

    @Test
    public void testExpressionWithSpace() {
        exchange.getIn().setBody("some text");
        assertPredicate("${in.body} contains 'some' && ${in.body} contains 'text'", true);
    }

    @Test
    public void testExpressionWithTabs() {
        exchange.getIn().setBody("some text");
        assertPredicate("${in.body} contains 'some' &&\t${in.body} contains 'text'", true);
    }

    @Test
    public void testUnixMultiLineExpression() {
        exchange.getIn().setBody("some text");
        assertPredicate("${in.body} contains 'some' &&\n${in.body} contains 'text'", true);
    }

    @Test
    public void testWindowsMultiLineExpression() {
        exchange.getIn().setBody("some text");
        assertPredicate("${in.body} contains 'some' &&\r\n${in.body} contains 'text'", true);
    }

    @Test
    public void testMacMultiLineExpression() {
        exchange.getIn().setBody("some text");
        assertPredicate("${in.body} contains 'some' &&\r${in.body} contains 'text'", true);
    }

    @Test
    public void testExpressionWithMultiLineString() {
        exchange.getIn().setBody("\tsome\nmulti\rline\r\ntext");
        assertPredicate("${in.body} == '\tsome\nmulti\rline\r\ntext'", true);
    }
}
