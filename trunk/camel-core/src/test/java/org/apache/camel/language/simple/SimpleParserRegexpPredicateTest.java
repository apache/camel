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
package org.apache.camel.language.simple;

import org.apache.camel.ExchangeTestSupport;
import org.apache.camel.Predicate;

/**
 * Unit test regexp function as the reg exp value should be template text only
 * and not any embedded functions etc.
 */
public class SimpleParserRegexpPredicateTest extends ExchangeTestSupport {

    public void testSimpleRegexp() throws Exception {
        exchange.getIn().setBody("12.34.5678");

        SimplePredicateParser parser = new SimplePredicateParser("${body} regex '^\\d{2}\\.\\d{2}\\.\\d{4}$'", true);
        Predicate pre = parser.parsePredicate();

        assertTrue(pre.matches(exchange));
        
        exchange.getIn().setBody("12.2a.22ab");
        assertFalse(pre.matches(exchange));
    }

}
