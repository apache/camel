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
package org.apache.camel.language;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Expression;
import org.apache.camel.IsSingleton;
import org.apache.camel.Predicate;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.service.ServiceSupport;
import org.junit.Test;

public class LanguageServiceTest extends ContextTestSupport {

    private MyLanguage my = new MyLanguage();

    @Override
    protected Registry createRegistry() throws Exception {
        Registry jndi = super.createRegistry();
        jndi.bind("my", my);
        return jndi;
    }

    @Test
    public void testLanguageService() throws Exception {
        MyLanguage myl = (MyLanguage)context.resolveLanguage("my");
        assertNotNull(myl);
        assertEquals("Started", myl.getState());
        // simple language is resolved by default hence why there is 2
        assertEquals(2, context.getLanguageNames().size());

        // resolve again, should find same instance
        MyLanguage myl2 = (MyLanguage)context.resolveLanguage("my");
        assertNotNull(myl2);
        assertSame(myl, myl2);
        assertEquals("Started", myl2.getState());
        // simple language is resolved by default hence why there is 2
        assertEquals(2, context.getLanguageNames().size());

        context.stop();
        assertEquals("Stopped", myl.getState());
        assertTrue(context.getLanguageNames().isEmpty());
    }

    @Test
    public void testNonSingletonLanguage() throws Exception {
        Language tol = context.resolveLanguage("tokenize");
        assertNotNull(tol);
        // simple language is resolved by default hence why there is 2
        assertEquals(2, context.getLanguageNames().size());

        // resolve again, should find another instance
        Language tol2 = context.resolveLanguage("tokenize");
        assertNotNull(tol2);
        assertNotSame(tol, tol2);
        // simple language is resolved by default hence why there is 2
        assertEquals(2, context.getLanguageNames().size());

        context.stop();
        assertTrue(context.getLanguageNames().isEmpty());
    }

    public class MyLanguage extends ServiceSupport implements Language, IsSingleton {

        private String state;

        @Override
        public Predicate createPredicate(String expression) {
            return PredicateBuilder.constant(true);

        }

        @Override
        public Expression createExpression(String expression) {
            return ExpressionBuilder.constantExpression(expression);
        }

        public String getState() {
            return state;
        }

        @Override
        protected void doStart() throws Exception {
            state = "Started";

        }

        @Override
        protected void doStop() throws Exception {
            state = "Stopped";
        }

        @Override
        public boolean isSingleton() {
            return true;
        }
    }
}
