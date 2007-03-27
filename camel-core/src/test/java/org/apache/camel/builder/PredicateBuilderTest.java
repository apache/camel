/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.builder;

import org.apache.camel.TestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.Message;
import static org.apache.camel.builder.PredicateBuilder.*;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultCamelContext;

import java.util.Arrays;

/**
 * @version $Revision$
 */
public class PredicateBuilderTest extends TestSupport {
    protected Exchange exchange = new DefaultExchange(new DefaultCamelContext());

    public void testRegexTokenize() throws Exception {
        Expression<Exchange> locationHeader = ExpressionBuilder.headerExpression("location");

        Predicate<Exchange> predicate = regex(locationHeader, "[a-zA-Z]+,London,UK");
        assertPredicate(predicate, exchange, true);

        predicate = regex(locationHeader, "[a-zA-Z]+,Westminster,[a-zA-Z]+");
        assertPredicate(predicate, exchange, false);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Message in = exchange.getIn();
        in.setBody("Hello there!");
        in.setHeader("name", "James");
        in.setHeader("location", "Islington,London,UK");
    }
}
