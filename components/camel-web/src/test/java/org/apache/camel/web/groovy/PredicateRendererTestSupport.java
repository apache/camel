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

import junit.framework.TestCase;
import org.apache.camel.Predicate;
import org.apache.camel.builder.Builder;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.builder.ValueBuilder;
import org.apache.camel.web.util.PredicateRenderer;

/**
 * 
 */
public class PredicateRendererTestSupport extends TestCase {

    protected static ValueBuilder body() {
        return Builder.body();
    }

    protected static ValueBuilder constant(Object value) {
        return Builder.constant(value);
    }

    protected static ValueBuilder header(String name) {
        return Builder.header(name);
    }

    protected static Predicate not(Predicate predicate) {
        return PredicateBuilder.not(predicate);
    }

    protected static Predicate and(Predicate p1, Predicate p2) {
        return PredicateBuilder.and(p1, p2);
    }

    protected static Predicate or(Predicate p1, Predicate p2) {
        return PredicateBuilder.or(p1, p2);
    }

    protected void assertMatch(String expectedPredicate, Predicate predicate) throws Exception {
        StringBuilder sb = new StringBuilder();
        PredicateRenderer.render(sb, predicate);

        assertEquals(expectedPredicate, sb.toString());
    }

}
