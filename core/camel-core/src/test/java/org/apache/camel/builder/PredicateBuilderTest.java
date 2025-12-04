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

package org.apache.camel.builder;

import static org.apache.camel.builder.Builder.constant;
import static org.apache.camel.builder.PredicateBuilder.in;
import static org.apache.camel.builder.PredicateBuilder.not;

import java.util.Arrays;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Predicate;
import org.apache.camel.TestSupport;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.Animal;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PredicateBuilderTest extends TestSupport {

    protected final Exchange exchange = new DefaultExchange(new DefaultCamelContext());

    @Test
    public void testRegexPredicates() {
        assertMatches(header("location").regex("[a-zA-Z]+,London,UK"));
        assertDoesNotMatch(header("location").regex("[a-zA-Z]+,Westminster,[a-zA-Z]+"));
    }

    @Test
    public void testPredicates() {
        assertMatches(header("name").isEqualTo(constant("James")));
        assertMatches(not(header("name").isEqualTo(constant("Claus"))));

        assertMatches(header("size").isEqualTo(10));
        assertMatches(header("size").isEqualTo("10"));
    }

    @Test
    public void testFailingPredicates() {
        assertDoesNotMatch(header("name").isEqualTo(constant("Hiram")));
        assertDoesNotMatch(header("size").isGreaterThan(constant(100)));
        assertDoesNotMatch(not(header("size").isLessThan(constant(100))));
    }

    @Test
    public void testCompoundOrPredicates() {
        Predicate p1 = header("name").isEqualTo(constant("Hiram"));
        Predicate p2 = header("size").isGreaterThanOrEqualTo(constant(10));
        Predicate or = PredicateBuilder.or(p1, p2);

        assertMatches(or);
    }

    @Test
    public void testCompoundAndPredicates() {
        Predicate p1 = header("name").isEqualTo(constant("James"));
        Predicate p2 = header("size").isGreaterThanOrEqualTo(constant(10));
        Predicate and = PredicateBuilder.and(p1, p2);

        assertMatches(and);
    }

    @Test
    public void testCompoundAndPredicatesVarargs() {
        Predicate p1 = header("name").isEqualTo(constant("James"));
        Predicate p2 = header("size").isGreaterThanOrEqualTo(constant(10));
        Predicate p3 = header("location").contains(constant("London"));
        Predicate and = PredicateBuilder.and(p1, p2, p3);

        assertMatches(and);
    }

    @Test
    public void testOrSignatures() {
        Predicate p1 = header("name").isEqualTo(constant("does-not-apply"));
        Predicate p2 = header("size").isGreaterThanOrEqualTo(constant(10));
        Predicate p3 = header("location").contains(constant("does-not-apply"));

        // check method signature with two parameters
        assertMatches(PredicateBuilder.or(p1, p2));
        assertMatches(PredicateBuilder.or(p2, p3));

        // check method signature with varargs
        assertMatches(PredicateBuilder.in(p1, p2, p3));
        assertMatches(PredicateBuilder.or(p1, p2, p3));

        // maybe a list of predicates?
        assertMatches(PredicateBuilder.in(Arrays.asList(p1, p2, p3)));
        assertMatches(PredicateBuilder.or(Arrays.asList(p1, p2, p3)));
    }

    @Test
    public void testCompoundAndOrPredicates() {
        Predicate p1 = header("name").isEqualTo(constant("Hiram"));
        Predicate p2 = header("size").isGreaterThan(constant(100));
        Predicate p3 = header("location").contains("London");
        Predicate and = PredicateBuilder.and(p1, p2);
        Predicate andor = PredicateBuilder.or(and, p3);

        assertMatches(andor);
    }

    @Test
    public void testPredicateIn() {
        assertMatches(in(header("name").isEqualTo("Hiram"), header("name").isEqualTo("James")));
    }

    @Test
    public void testValueIn() {
        assertMatches(header("name").in("Hiram", "Jonathan", "James", "Claus"));
    }

    @Test
    public void testEmptyHeaderValueIn() {
        // there is no header with xxx
        assertDoesNotMatch(header("xxx").in("Hiram", "Jonathan", "James", "Claus"));
    }

    @Test
    public void testStartsWith() {
        assertMatches(header("name").startsWith("J"));
        assertMatches(header("name").startsWith("James"));
        assertDoesNotMatch(header("name").startsWith("C"));

        assertMatches(header("size").startsWith("1"));
        assertMatches(header("size").startsWith("10"));
        assertDoesNotMatch(header("size").startsWith("99"));
        assertDoesNotMatch(header("size").startsWith("9"));

        assertMatches(header("size").startsWith(1));
        assertMatches(header("size").startsWith(10));
        assertDoesNotMatch(header("size").startsWith(99));
        assertDoesNotMatch(header("size").startsWith(9));
    }

    @Test
    public void testEndsWith() {
        assertMatches(header("name").endsWith("mes"));
        assertMatches(header("name").endsWith("James"));
        assertDoesNotMatch(header("name").endsWith("world"));

        assertMatches(header("size").endsWith("0"));
        assertMatches(header("size").endsWith("10"));
        assertDoesNotMatch(header("size").endsWith("99"));
        assertDoesNotMatch(header("size").endsWith("9"));

        assertMatches(header("size").endsWith(0));
        assertMatches(header("size").endsWith(10));
        assertDoesNotMatch(header("size").endsWith(99));
        assertDoesNotMatch(header("size").endsWith(9));
    }

    @Test
    public void testNot() {
        assertMatches(body().not().isInstanceOf(Integer.class));
        assertMatches(header("name").not().isEqualTo("Claus"));
        assertMatches(header("size").not().isLessThan(7));
        assertDoesNotMatch(header("name").not().isEqualTo("James"));
    }

    @Test
    public void testMethod() {
        Animal tiger = new Animal("Tony", true);
        exchange.getMessage().setBody(tiger);

        assertMatches(PredicateBuilder.isEqualTo(bodyAs(Animal.class).method("getName"), constant("Tony")));
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
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
