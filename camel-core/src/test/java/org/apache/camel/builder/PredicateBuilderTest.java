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
import static org.apache.camel.builder.PredicateBuilder.in;
import static org.apache.camel.builder.PredicateBuilder.not;

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
        assertMatches(not(header("name").isEqualTo(constant("Claus"))));
    }

    public void testFailingPredicates() throws Exception {
        assertDoesNotMatch(header("name").isEqualTo(constant("Hiram")));
        assertDoesNotMatch(header("size").isGreaterThan(constant(100)));
        assertDoesNotMatch(not(header("size").isLessThan(constant(100))));
    }

    public void testCompoundOrPredicates() throws Exception {
        Predicate p1 = header("name").isEqualTo(constant("Hiram"));
        Predicate p2 = header("size").isGreaterThanOrEqualTo(constant(10));
        Predicate or = PredicateBuilder.or(p1, p2);

        assertMatches(or);
    }

    public void testCompoundAndPredicates() throws Exception {
        Predicate p1 = header("name").isEqualTo(constant("James"));
        Predicate p2 = header("size").isGreaterThanOrEqualTo(constant(10));
        Predicate and = PredicateBuilder.and(p1, p2);

        assertMatches(and);
    }

    public void testCompoundAndOrPredicates() throws Exception {
        Predicate p1 = header("name").isEqualTo(constant("Hiram"));
        Predicate p2 = header("size").isGreaterThan(constant(100));
        Predicate p3 = header("location").contains("London");
        Predicate and = PredicateBuilder.and(p1, p2);
        Predicate andor = PredicateBuilder.or(and, p3);

        assertMatches(andor);
    }

    public void testPredicateIn() throws Exception {
        assertMatches(in(header("name").isEqualTo("Hiram"), header("name").isEqualTo("James")));
    }

    public void testValueIn() throws Exception {
        assertMatches(header("name").in("Hiram", "Jonathan", "James", "Claus"));
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

    protected void assertMatches(Predicate predicate) {
        assertPredicateMatches(predicate, exchange);
    }

    protected void assertDoesNotMatch(Predicate predicate) {
        assertPredicateDoesNotMatch(predicate, exchange);
    }

}
