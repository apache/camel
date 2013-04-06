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
package org.apache.camel.lanaguage.sql;

import java.util.List;

import org.apache.camel.Expression;
import org.apache.camel.builder.sql.Person;
import org.apache.camel.builder.sql.SqlTest;
import org.apache.camel.spi.Language;
import org.junit.Test;

/**
 * @version 
 */
public class SqlLanguageTest extends SqlTest {

    @Test
    public void testExpression() throws Exception {
        Language language = assertResolveLanguage(getLanguageName());

        Expression expression = language.createExpression("SELECT * FROM org.apache.camel.builder.sql.Person where city = 'London'");        
        List<?> value = expression.evaluate(exchange, List.class);
        assertEquals("List size", 2, value.size());

        for (Object person : value) {
            log.info("Found: " + person);
        }
    }

    @Test
    public void testExpressionWithHeaderVariable() throws Exception {
        Language language = assertResolveLanguage(getLanguageName());

        Expression expression = language.createExpression("SELECT * FROM org.apache.camel.builder.sql.Person where name = :fooHeader");
        List<?> value = expression.evaluate(exchange, List.class);
        assertEquals("List size", 1, value.size());

        for (Object person : value) {
            log.info("Found: " + person);

            assertEquals("name", "James", ((Person) person).getName());
        }
    }

    @Test
    public void testPredicates() throws Exception {
        Language language = assertResolveLanguage(getLanguageName());
        assertPredicate(language.createPredicate("SELECT * FROM org.apache.camel.builder.sql.Person where city = 'London'"), exchange, true);
        assertPredicate(language.createPredicate("SELECT * FROM org.apache.camel.builder.sql.Person where city = 'Manchester'"), exchange, false);
    }

    @Test
    public void testPredicateWithHeaderVariable() throws Exception {
        Language language = assertResolveLanguage(getLanguageName());
        assertPredicate(language.createPredicate("SELECT * FROM org.apache.camel.builder.sql.Person where name = :fooHeader"), exchange, true);
    }

    protected String getLanguageName() {
        return "sql";
    }
    
}
