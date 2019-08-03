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

import org.apache.camel.LanguageTestSupport;
import org.junit.Test;

public class SpelTest extends LanguageTestSupport {

    @Test
    public void testSpelExpressions() throws Exception {
        assertExpression("#{exchange}", exchange);
        assertExpression("#{exchange.getIn().body}", "<hello id='m123'>world!</hello>");
        assertExpression("#{getRequest().body}", "<hello id='m123'>world!</hello>");
        assertExpression("#{request.body}", "<hello id='m123'>world!</hello>");
        assertExpression("#{message.body}", "<hello id='m123'>world!</hello>");
        assertExpression("#{request.Headers['foo']}", "abc");
        assertExpression("#{getRequest().Headers['foo']}", "abc");
        assertExpression("#{request.Headers['foo'] == 'abc'}", true);
        assertExpression("#{request.headers['bar'] == 123}", true);
        assertExpression("#{request.headers['bar'] > 10}", true);
        assertExpression("#{request.Headers.foo}", "abc");
        assertExpression("#{getRequest().Headers.foo}", "abc");
        assertExpression("#{request.Headers.foo == 'abc'}", true);
        assertExpression("#{request.headers.bar == 123}", true);
        assertExpression("#{request.headers.bar > 10}", true);
        assertExpression("#{6 / -3}", -2);
    }

    @Test
    public void testSpelPredicates() throws Exception {
        assertPredicate("#{request.headers['foo'].startsWith('a')}");
        assertPredicate("#{request.headers['foo'] == 'abc'}");
        assertPredicateFails("#{request.headers['foo'] == 'badString'}");
        assertPredicate("#{request.headers.foo.startsWith('a')}");
        assertPredicate("#{request.headers.foo == 'abc'}");
        assertPredicateFails("#{request.headers.foo == 'badString'}");
        assertPredicate("#{message.headers.foo == 'abc'}");
    }
    
    @Test
    public void testResponseCreatesOutMessage() throws Exception {
        assertExpression("#{response.body}", null);
        assertTrue(exchange.hasOut());
    }

    @Override
    protected String getLanguageName() {
        return "spel";
    }
}
