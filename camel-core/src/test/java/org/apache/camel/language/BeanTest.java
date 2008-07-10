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

import javax.naming.Context;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Header;
import org.apache.camel.LanguageTestSupport;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultExchange;

/**
 * @version $Revision$
 */
public class BeanTest extends LanguageTestSupport {

    public void testSimpleExpressions() throws Exception {
        assertExpression("foo.cheese", "abc");
    }

    public void testPredicates() throws Exception {
        assertPredicate("foo.isFooHeaderAbc");
    }

    protected String getLanguageName() {
        return "bean";
    }

    @Override
    protected Context createJndiContext() throws Exception {
        Context context = super.createJndiContext();
        context.bind("foo", new MyBean());
        return context;
    }

    public static class MyBean {
        public Object cheese(Exchange exchange) {
            Message in = exchange.getIn();
            return in.getHeader("foo");
        }

        public boolean isFooHeaderAbc(@Header(name = "foo")String foo) {
            return "abc".equals(foo);
        }
    }
}