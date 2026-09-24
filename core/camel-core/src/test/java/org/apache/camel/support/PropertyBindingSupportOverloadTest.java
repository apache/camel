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
package org.apache.camel.support;

import java.math.BigDecimal;

import org.apache.camel.ContextTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Constructors and factory methods that are overloaded for the given parameters use the most specific, as Java does.
 */
public class PropertyBindingSupportOverloadTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testConstructorOverloadedWithNumberTypes() throws Exception {
        // BigDecimal has constructors with int, long and BigInteger (among others)
        Object answer = PropertyBindingSupport.newInstanceConstructorParameters(context, BigDecimal.class, "5");
        assertEquals(new BigDecimal(5), answer);
    }

    @Test
    public void testFactoryMethodOverloadedWithIntAndLong() throws Exception {
        Object answer = PropertyBindingSupport.newInstanceFactoryParameters(context, MyFactory.class, "create", "5");
        assertEquals("int:5", answer);
    }

    @Test
    public void testConstructorOverloadedWithBeanTypes() throws Exception {
        context.getRegistry().bind("myBean", new StringBuilder("Camel"));
        Object answer = PropertyBindingSupport.newInstanceConstructorParameters(context, MyBeanUser.class, "#bean:myBean");
        assertEquals("StringBuilder:Camel", answer.toString());
    }

    public static final class MyFactory {

        private MyFactory() {
        }

        public static String create(int value) {
            return "int:" + value;
        }

        public static String create(long value) {
            return "long:" + value;
        }
    }

    public static final class MyBeanUser {

        private final String text;

        public MyBeanUser(CharSequence value) {
            this.text = "CharSequence:" + value;
        }

        public MyBeanUser(StringBuilder value) {
            this.text = "StringBuilder:" + value;
        }

        @Override
        public String toString() {
            return text;
        }
    }
}
