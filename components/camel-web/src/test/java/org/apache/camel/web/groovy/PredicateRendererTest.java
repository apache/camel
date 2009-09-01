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

package org.apache.camel.web.groovy;

import org.apache.camel.Predicate;
import org.apache.camel.builder.PredicateBuilder;
import org.junit.Ignore;
import org.junit.Test;

import static org.apache.camel.builder.PredicateBuilder.in;

/**
 * 
 */
public class PredicateRendererTest extends PredicateRendererTestSupport {

    @Test
    public void testNot() throws Exception {
        String expectedPredicate = "not(header(\"name\").isEqualTo(\"Claus\"))";
        Predicate predicate = not(header("name").isEqualTo(constant("Claus")));
        assertMatch(expectedPredicate, predicate);
    }

    @Test
    public void testAnd() throws Exception {
        String expectedPredicate = "and(header(\"name\").isEqualTo(\"James\"), header(\"size\").isGreaterThanOrEqualTo(10))";
        Predicate p1 = header("name").isEqualTo(constant("James"));
        Predicate p2 = header("size").isGreaterThanOrEqualTo(constant(10));
        Predicate and = PredicateBuilder.and(p1, p2);
        assertMatch(expectedPredicate, and);
    }

    @Test
    public void testOr() throws Exception {
        String expectedPredicate = "or(header(\"name\").isEqualTo(\"Hiram\"), header(\"size\").isGreaterThanOrEqualTo(10))";
        Predicate p1 = header("name").isEqualTo(constant("Hiram"));
        Predicate p2 = header("size").isGreaterThanOrEqualTo(constant(10));
        Predicate or = PredicateBuilder.or(p1, p2);
        assertMatch(expectedPredicate, or);
    }

    @Test
    public void testPredicateIn() throws Exception {
        String expectedPredicate = "in(header(\"name\").isEqualTo(\"Hiram\"), header(\"name\").isEqualTo(\"James\"))";
        Predicate predicate = in(header("name").isEqualTo("Hiram"), header("name").isEqualTo("James"));
        assertMatch(expectedPredicate, predicate);
    }

    @Test
    public void testValueIn() throws Exception {
        String expectedPredicate = "header(\"name\").in(\"Hiram\", \"Jonathan\", \"James\", \"Claus\")";
        Predicate predicate = header("name").in("Hiram", "Jonathan", "James", "Claus");
        assertMatch(expectedPredicate, predicate);
    }

    @Test
    public void testIsEqualToString() throws Exception {
        String expectedPredicate = "header(\"name\").isEqualTo(\"James\")";
        Predicate predicate = header("name").isEqualTo(constant("James"));
        assertMatch(expectedPredicate, predicate);

    }

    @Test
    public void testIsEqualToConstant() throws Exception {
        String expectedPredicate = "header(\"name\").isEqualTo(100)";
        Predicate predicate = header("name").isEqualTo(constant(100));
        assertMatch(expectedPredicate, predicate);
    }

    @Test
    public void testIsNotEqualTo() throws Exception {
        String expectedPredicate = "header(\"name\").isNotEqualTo(\"James\")";
        Predicate predicate = header("name").isNotEqualTo(constant("James"));
        assertMatch(expectedPredicate, predicate);
    }

    @Test
    public void testIsLessThan() throws Exception {
        String expectedPredicate = "header(\"name\").isLessThan(\"James\")";
        Predicate predicate = header("name").isLessThan(constant("James"));
        assertMatch(expectedPredicate, predicate);
    }

    @Test
    public void testIsLessThanOrEqualTo() throws Exception {
        String expectedPredicate = "header(\"name\").isLessThanOrEqualTo(\"James\")";
        Predicate predicate = header("name").isLessThanOrEqualTo(constant("James"));
        assertMatch(expectedPredicate, predicate);
    }

    @Test
    public void testIsGreaterThan() throws Exception {
        String expectedPredicate = "header(\"name\").isGreaterThan(\"James\")";
        Predicate predicate = header("name").isGreaterThan(constant("James"));
        assertMatch(expectedPredicate, predicate);
    }

    @Test
    public void testIsGreaterThanOrEqualTo() throws Exception {
        String expectedPredicate = "header(\"name\").isGreaterThanOrEqualTo(\"James\")";
        Predicate predicate = header("name").isGreaterThanOrEqualTo(constant("James"));
        assertMatch(expectedPredicate, predicate);
    }

    @Test
    public void testContains() throws Exception {
        String expectedPredicate = "header(\"name\").contains(\"James\")";
        Predicate predicate = header("name").contains(constant("James"));
        assertMatch(expectedPredicate, predicate);
    }

    @Test
    public void testIsNull() throws Exception {
        String expectedPredicate = "header(\"name\").isNull()";
        Predicate predicate = header("name").isNull();
        assertMatch(expectedPredicate, predicate);
    }

    @Test
    public void testIsNotNull() throws Exception {
        String expectedPredicate = "header(\"name\").isNotNull()";
        Predicate predicate = header("name").isNotNull();
        assertMatch(expectedPredicate, predicate);
    }

    @Ignore("Need to fix this test")
    @Test
    // TODO: fix this test!
    public void fixmeTestIsInstanceOf() throws Exception {
        String expectedPredicate = "header(\"name\").isNull()";
        Predicate predicate = header("name").isNull();
        assertMatch(expectedPredicate, predicate);
    }

    @Test
    public void testStartsWith() throws Exception {
        String expectedPredicate = "header(\"name\").startsWith(\"James\")";
        Predicate predicate = header("name").startsWith("James");
        assertMatch(expectedPredicate, predicate);
    }

    @Test
    public void testEndsWith() throws Exception {
        String expectedPredicate = "header(\"name\").endsWith(\"James\")";
        Predicate predicate = header("name").endsWith("James");
        assertMatch(expectedPredicate, predicate);
    }

    @Test
    public void testRegex() throws Exception {
        String expectedPredicate = "header(\"name\").regex(\"[a-zA-Z]+,London,UK\")";
        Predicate predicate = header("name").regex("[a-zA-Z]+,London,UK");
        assertMatch(expectedPredicate, predicate);
    }

}
