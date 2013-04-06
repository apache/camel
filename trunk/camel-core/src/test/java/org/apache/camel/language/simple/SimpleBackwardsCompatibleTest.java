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
package org.apache.camel.language.simple;

import org.apache.camel.LanguageTestSupport;
import org.apache.camel.Predicate;

/**
 *
 */
public class SimpleBackwardsCompatibleTest extends LanguageTestSupport {

    @Override
    protected String getLanguageName() {
        return "simple";
    }

    public void testSimpleBody() throws Exception {
        assertExpression(exchange, "${body}", "<hello id='m123'>world!</hello>");
        assertExpression(exchange, "$simple{body}", "<hello id='m123'>world!</hello>");
        assertExpression(exchange, "body", "<hello id='m123'>world!</hello>");

        assertPredicate("${body}", true);
        assertPredicate("body", true);
    }

    public void testSimpleHeader() throws Exception {
        exchange.getIn().setHeader("foo", 123);
        assertExpression(exchange, "${header.foo}", 123);
        assertExpression(exchange, "header.foo", 123);

        assertPredicate("${header.foo}", true);
        assertPredicate("header.foo", true);

        assertPredicate("${header.unknown}", false);
        assertPredicate("header.unknown", false);
    }

    public void testSimpleLogicalAnd() throws Exception {
        exchange.getIn().setBody("Hello");
        exchange.getIn().setHeader("high", true);
        exchange.getIn().setHeader("foo", 123);

        SimplePredicateParser parser = new SimplePredicateParser("${header.high} == true and ${header.foo} == 123", true);
        Predicate pre = parser.parsePredicate();

        assertTrue("Should match", pre.matches(exchange));
    }

    public void testSimpleLogicalOr() throws Exception {
        exchange.getIn().setBody("Hello");
        exchange.getIn().setHeader("high", true);
        exchange.getIn().setHeader("foo", 123);

        SimplePredicateParser parser = new SimplePredicateParser("${header.high} == false or ${header.foo} == 123", true);
        Predicate pre = parser.parsePredicate();

        assertTrue("Should match", pre.matches(exchange));
    }

}
