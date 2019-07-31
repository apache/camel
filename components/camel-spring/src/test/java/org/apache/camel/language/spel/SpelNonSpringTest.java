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
package org.apache.camel.language.spel;

import org.apache.camel.ExpressionEvaluationException;
import org.apache.camel.LanguageTestSupport;
import org.apache.camel.language.spel.bean.Dummy;
import org.junit.Test;

/**
 * Test access to beans defined in non-Spring context from SpEL expressions/predicates.
 */
public class SpelNonSpringTest extends LanguageTestSupport {

    @Test
    public void testSpelBeanExpressions() throws Exception {
        context.getRegistry().bind("myDummy", new Dummy());

        assertExpression("#{@myDummy.foo == 'xyz'}", true);
        assertExpression("#{@myDummy.bar == 789}", true);
        assertExpression("#{@myDummy.bar.toString()}", "789");
        try {
            assertExpression("#{@notFound}", null);
        } catch (ExpressionEvaluationException ex) {
            assertStringContains(ex.getMessage(), "Could not resolve bean reference against Registry");
        }
    }
    
    @Test
    public void testSpelBeanPredicates() throws Exception {
        context.getRegistry().bind("myDummy", new Dummy());
        
        assertPredicate("@myDummy.foo == 'xyz'");
        assertPredicate("@myDummy.bar == 789");
        assertPredicate("@myDummy.bar instanceof T(Integer)");
    }

    @Override
    protected String getLanguageName() {
        return "spel";
    }
}
