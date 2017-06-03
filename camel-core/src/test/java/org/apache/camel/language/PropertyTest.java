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
import org.apache.camel.language.property.ExchangePropertyLanguage;

public class PropertyTest extends LanguageTestSupport {

    public void testPropertyExpressions() throws Exception {
        assertExpression("quote", "Camel rocks");
    }

    public void testPredicates() throws Exception {
        assertPredicate("quote");
    }

    protected String getLanguageName() {
        return "exchangeProperty";
    }

    public void testSingleton() {
        ExchangePropertyLanguage prop = new ExchangePropertyLanguage();
        assertTrue(prop.isSingleton());
    }

    @Override
    protected void populateExchange(Exchange exchange) {
        super.populateExchange(exchange);
        exchange.setProperty("quote", "Camel rocks");
    }
}
