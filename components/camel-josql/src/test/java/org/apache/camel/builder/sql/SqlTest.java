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

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Message;
import org.apache.camel.TestSupport;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;

import static org.apache.camel.builder.sql.SqlBuilder.sql;

/**
 * @version $Revision$
 */
public class SqlTest extends TestSupport {

    protected CamelContext context = new DefaultCamelContext();
    protected Exchange exchange;

    public void testExpression() throws Exception {
        Expression expression = sql("SELECT * FROM org.apache.camel.builder.sql.Person where city = 'London'");
        Object value = expression.evaluate(exchange);
        assertIsInstanceOf(List.class, value);

        List list = (List)value;
        assertEquals("List size", 2, list.size());

        for (Object person : list) {
            log.info("Found: " + person);
        }
    }

    public void testExpressionWithHeaderVariable() throws Exception {
        Expression expression = sql("SELECT * FROM org.apache.camel.builder.sql.Person where name = :fooHeader");
        Object value = expression.evaluate(exchange);
        assertIsInstanceOf(List.class, value);

        List<Person> list = (List<Person>)value;
        assertEquals("List size", 1, list.size());

        for (Person person : list) {
            log.info("Found: " + person);

            assertEquals("name", "James", person.getName());
        }
    }

    public void testPredicates() throws Exception {
        assertPredicate(sql("SELECT * FROM org.apache.camel.builder.sql.Person where city = 'London'"), exchange, true);
        assertPredicate(sql("SELECT * FROM org.apache.camel.builder.sql.Person where city = 'Manchester'"), exchange, false);
    }

    public void testPredicateWithHeaderVariable() throws Exception {
        assertPredicate(sql("SELECT * FROM org.apache.camel.builder.sql.Person where name = :fooHeader"), exchange, true);
    }

    protected void setUp() throws Exception {
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
