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
package org.apache.camel.builder;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Predicate;
import org.apache.camel.TestSupport;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;

import static org.apache.camel.builder.Builder.constant;

/**
 * @version $Revision$
 */
public class PredicateBuilderTest extends TestSupport {
    protected Exchange exchange = new DefaultExchange(new DefaultCamelContext());

    public void testRegexPredicates() throws Exception {
        assertMatches(header("location").regex("[a-zA-Z]+,London,UK"));
        assertDoesNotMatch(header("location").regex("[a-zA-Z]+,Westminster,[a-zA-Z]+"));
    }

    public void testPredicates() throws Exception {
        assertMatches(header("name").isEqualTo(constant("James")));
    }

    public void testFailingPredicates() throws Exception {
        assertDoesNotMatch(header("name").isEqualTo(constant("Hiram")));
        assertDoesNotMatch(header("size").isGreaterThan(constant(100)));
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Message in = exchange.getIn();
        in.setBody("Hello there!");
        in.setHeader("name", "James");
        in.setHeader("location", "Islington,London,UK");
        in.setHeader("size", 10);
    }

    protected void assertMatches(Predicate<Exchange> predicate) {
        assertPredicateMatches(predicate, exchange);
    }

    protected void assertDoesNotMatch(Predicate<Exchange> predicate) {
        assertPredicateDoesNotMatch(predicate, exchange);
    }

}
