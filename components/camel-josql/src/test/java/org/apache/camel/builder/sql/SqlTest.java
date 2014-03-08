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
package org.apache.camel.builder.sql;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;

import static org.apache.camel.builder.sql.SqlBuilder.sql;

/**
 * @version 
 */
public class SqlTest extends CamelTestSupport {

    protected Exchange exchange;

    @Test
    public void testExpression() throws Exception {
        Expression expression = sql("SELECT * FROM org.apache.camel.builder.sql.Person where city = 'London'");
        List<?> value = expression.evaluate(exchange, List.class);
        assertEquals("List size", 2, value.size());

        for (Object person : value) {
            log.info("Found: " + person);
        }
    }

    @Test
    public void testExpressionWithHeaderVariable() throws Exception {
        Expression expression = sql("SELECT * FROM org.apache.camel.builder.sql.Person where name = :fooHeader");
        List<?> value = expression.evaluate(exchange, List.class);
        assertEquals("List size", 1, value.size());

        for (Object person : value) {
            log.info("Found: " + person);

            assertEquals("name", "James", ((Person)person).getName());
        }
    }

    @Test
    public void testPredicates() throws Exception {
        assertPredicate(sql("SELECT * FROM org.apache.camel.builder.sql.Person where city = 'London'"), exchange, true);
        assertPredicate(sql("SELECT * FROM org.apache.camel.builder.sql.Person where city = 'Manchester'"), exchange, false);
    }

    @Test
    public void testPredicateWithHeaderVariable() throws Exception {
        assertPredicate(sql("SELECT * FROM org.apache.camel.builder.sql.Person where name = :fooHeader"), exchange, true);
    }

    @Before
    public void setUp() throws Exception {        
        super.setUp();
        exchange = createExchange();
    }

    protected Exchange createExchange() {
        Exchange exchange = new DefaultExchange(context);
        Message message = exchange.getIn();
        message.setHeader("fooHeader", "James");

        Person[] people = {
            new Person("James", "London"), 
            new Person("Guillaume", "Normandy"), 
            new Person("Rob", "London"), 
            new Person("Hiram", "Tampa")
        };

        message.setBody(people);
        return exchange;
    }
}
