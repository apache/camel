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
package org.apache.camel.language;

import org.apache.camel.Exchange;
import org.apache.camel.LanguageTestSupport;
import org.apache.camel.Predicate;
import org.apache.camel.impl.JndiRegistry;
import org.junit.Test;

public class RefPredicateTest extends LanguageTestSupport {

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("myPredicate", new MyPredicate());
        return jndi;
    }

    @Test
    public void testExpression() throws Exception {
        exchange.getIn().setBody("Hello World");
        assertExpression("myPredicate", "true");

        exchange.getIn().setBody("Bye World");
        assertExpression("myPredicate", "false");
    }
 
    @Test
    public void testPredicates() throws Exception {
        exchange.getIn().setBody("Hello World");
        assertPredicate("myPredicate", true);

        exchange.getIn().setBody("Bye World");
        assertPredicate("myPredicate", false);
    }

    protected String getLanguageName() {
        return "ref";
    }

    private static class MyPredicate implements Predicate {

        @Override
        public boolean matches(Exchange exchange) {
            return exchange.getIn().getBody().equals("Hello World");
        }
    }
}
