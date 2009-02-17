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
import org.apache.camel.impl.JndiRegistry;

/**
 * @version $Revision$
 */
public class SimpleOperatorTest extends LanguageTestSupport {

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("generator", new MyFileNameGenerator());
        return jndi;
    }

    public void testEqualOperator() throws Exception {
        // string to string comparison
        assertExpression("${in.header.foo} == 'abc'", true);
        assertExpression("${in.header.foo} == abc", true);
        assertExpression("${in.header.foo} == 'def'", false);
        assertExpression("${in.header.foo} == def", false);
        assertExpression("${in.header.foo} == '1'", false);

        // integer to string comparioson
        assertExpression("${in.header.bar} == '123'", true);
        assertExpression("${in.header.bar} == 123", true);
        assertExpression("${in.header.bar} == '444'", false);
        assertExpression("${in.header.bar} == 444", false);
        assertExpression("${in.header.bar} == '1'", false);
    }

    public void testNotEqualOperator() throws Exception {
        // string to string comparison
        assertExpression("${in.header.foo} != 'abc'", false);
        assertExpression("${in.header.foo} != abc", false);
        assertExpression("${in.header.foo} != 'def'", true);
        assertExpression("${in.header.foo} != def", true);
        assertExpression("${in.header.foo} != '1'", true);

        // integer to string comparioson
        assertExpression("${in.header.bar} != '123'", false);
        assertExpression("${in.header.bar} != 123", false);
        assertExpression("${in.header.bar} != '444'", true);
        assertExpression("${in.header.bar} != 444", true);
        assertExpression("${in.header.bar} != '1'", true);
    }

    public void testGreatherThanOperator() throws Exception {
        // string to string comparison
        assertExpression("${in.header.foo} > 'aaa'", true);
        assertExpression("${in.header.foo} > aaa", true);
        assertExpression("${in.header.foo} > 'def'", false);
        assertExpression("${in.header.foo} > def", false);

        // integer to string comparioson
        assertExpression("${in.header.bar} > '100'", true);
        assertExpression("${in.header.bar} > 100", true);
        assertExpression("${in.header.bar} > '123'", false);
        assertExpression("${in.header.bar} > 123", false);
        assertExpression("${in.header.bar} > '200'", false);
    }

    public void testGreatherThanOrEqualOperator() throws Exception {
        // string to string comparison
        assertExpression("${in.header.foo} >= 'aaa'", true);
        assertExpression("${in.header.foo} >= aaa", true);
        assertExpression("${in.header.foo} >= 'abc'", true);
        assertExpression("${in.header.foo} >= abc", true);
        assertExpression("${in.header.foo} >= 'def'", false);

        // integer to string comparioson
        assertExpression("${in.header.bar} >= '100'", true);
        assertExpression("${in.header.bar} >= 100", true);
        assertExpression("${in.header.bar} >= '123'", true);
        assertExpression("${in.header.bar} >= 123", true);
        assertExpression("${in.header.bar} >= '200'", false);
    }

    public void testLessThanOperator() throws Exception {
        // string to string comparison
        assertExpression("${in.header.foo} < 'aaa'", false);
        assertExpression("${in.header.foo} < aaa", false);
        assertExpression("${in.header.foo} < 'def'", true);
        assertExpression("${in.header.foo} < def", true);

        // integer to string comparioson
        assertExpression("${in.header.bar} < '100'", false);
        assertExpression("${in.header.bar} < 100", false);
        assertExpression("${in.header.bar} < '123'", false);
        assertExpression("${in.header.bar} < 123", false);
        assertExpression("${in.header.bar} < '200'", true);
    }

    public void testLessThanOrEqualOperator() throws Exception {
        // string to string comparison
        assertExpression("${in.header.foo} <= 'aaa'", false);
        assertExpression("${in.header.foo} <= aaa", false);
        assertExpression("${in.header.foo} <= 'abc'", true);
        assertExpression("${in.header.foo} <= abc", true);
        assertExpression("${in.header.foo} <= 'def'", true);

        // integer to string comparioson
        assertExpression("${in.header.bar} <= '100'", false);
        assertExpression("${in.header.bar} <= 100", false);
        assertExpression("${in.header.bar} <= '123'", true);
        assertExpression("${in.header.bar} <= 123", true);
        assertExpression("${in.header.bar} <= '200'", true);
    }

    public void testIsNull() throws Exception {
        assertExpression("${in.header.foo} == null", false);
        assertExpression("${in.header.none} == null", true);
    }

    public void testIsNotNull() throws Exception {
        assertExpression("${in.header.foo} != null", true);
        assertExpression("${in.header.none} != null", false);
    }

    public void testRightOperatorIsSimpleLanauge() throws Exception {
        // operator on right side is also using ${ } placeholders
        assertExpression("${in.header.foo} == ${in.header.foo}", true);
        assertExpression("${in.header.foo} == ${in.header.bar}", false);
    }

    public void testRightOperatorIsBeanLanauge() throws Exception {
        // operator on right side is also using ${ } placeholders
        assertExpression("${in.header.foo} == ${bean:generator.generateFilename}", true);

        assertExpression("${in.header.bar} == ${bean:generator.generateId}", true);
        assertExpression("${in.header.bar} >= ${bean:generator.generateId}", true);
    }

    public void testConstains() throws Exception {
        assertExpression("${in.header.foo} contains 'a'", true);
        assertExpression("${in.header.foo} contains a", true);
        assertExpression("${in.header.foo} contains 'ab'", true);
        assertExpression("${in.header.foo} contains 'abc'", true);
        assertExpression("${in.header.foo} contains 'def'", false);
        assertExpression("${in.header.foo} contains def", false);
    }

    public void testNotConstains() throws Exception {
        assertExpression("${in.header.foo} not contains 'a'", false);
        assertExpression("${in.header.foo} not contains a", false);
        assertExpression("${in.header.foo} not contains 'ab'", false);
        assertExpression("${in.header.foo} not contains 'abc'", false);
        assertExpression("${in.header.foo} not contains 'def'", true);
        assertExpression("${in.header.foo} not contains def", true);
    }

    public void testRegex() throws Exception {
        assertExpression("${in.header.foo} regex '^a..$'", true);
        assertExpression("${in.header.foo} regex '^ab.$'", true);
        assertExpression("${in.header.foo} regex ^ab.$", true);
        assertExpression("${in.header.foo} regex ^d.*$", false);

        assertExpression("${in.header.bar} regex '^\\d{3}'", true);
        assertExpression("${in.header.bar} regex '^\\d{2}'", false);
        assertExpression("${in.header.bar} regex ^\\d{3}", true);
        assertExpression("${in.header.bar} regex ^\\d{2}", false);
    }

    public void testNotRegex() throws Exception {
        assertExpression("${in.header.foo} not regex '^a..$'", false);
        assertExpression("${in.header.foo} not regex '^ab.$'", false);
        assertExpression("${in.header.foo} not regex ^ab.$", false);
        assertExpression("${in.header.foo} not regex ^d.*$", true);

        assertExpression("${in.header.bar} not regex '^\\d{3}'", false);
        assertExpression("${in.header.bar} not regex '^\\d{2}'", true);
        assertExpression("${in.header.bar} not regex ^\\d{3}", false);
        assertExpression("${in.header.bar} not regex ^\\d{2}", true);
    }

    public void testIn() throws Exception {
        // string to string
        assertExpression("${in.header.foo} in 'foo,abc,def'", true);
        assertExpression("${in.header.foo} in ${bean:generator.generateFilename}", true);
        assertExpression("${in.header.foo} in foo,abc,def", true);
        assertExpression("${in.header.foo} in 'foo,def'", false);

        // integer to string
        assertExpression("${in.header.bar} in '100,123,200'", true);
        assertExpression("${in.header.bar} in 100,123,200", true);
        assertExpression("${in.header.bar} in ${bean:generator.generateId}", true);
        assertExpression("${in.header.bar} in '100,200'", false);
    }

    public void testNotIn() throws Exception {
        // string to string
        assertExpression("${in.header.foo} not in 'foo,abc,def'", false);
        assertExpression("${in.header.foo} not in ${bean:generator.generateFilename}", false);
        assertExpression("${in.header.foo} not in foo,abc,def", false);
        assertExpression("${in.header.foo} not in 'foo,def'", true);

        // integer to string
        assertExpression("${in.header.bar} not in '100,123,200'", false);
        assertExpression("${in.header.bar} not in 100,123,200", false);
        assertExpression("${in.header.bar} not in ${bean:generator.generateId}", false);
        assertExpression("${in.header.bar} not in '100,200'", true);
    }

    protected String getLanguageName() {
        return "simple";
    }

    public class MyFileNameGenerator {
        public String generateFilename(Exchange exchange) {
            return "abc";
        }

        public int generateId(Exchange exchange) {
            return 123;
        }
    }

}