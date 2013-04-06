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
import org.apache.camel.Expression;
import org.apache.camel.Header;
import org.apache.camel.LanguageTestSupport;
import org.apache.camel.Message;
import org.apache.camel.component.bean.MethodNotFoundException;
import org.apache.camel.language.bean.BeanLanguage;

/**
 * @version 
 */
public class BeanTest extends LanguageTestSupport {

    public void testSimpleExpressions() throws Exception {
        assertExpression("foo.cheese", "abc");
        assertExpression("foo?method=cheese", "abc");
    }

    public void testPredicates() throws Exception {
        assertPredicate("foo.isFooHeaderAbc");
        assertPredicate("foo?method=isFooHeaderAbc");
    }

    public void testBeanTypeExpression() throws Exception {
        Expression exp = BeanLanguage.bean(MyUser.class, null);
        Exchange exchange = createExchangeWithBody("Claus");

        Object result = exp.evaluate(exchange, Object.class);
        assertEquals("Hello Claus", result);
    }

    public void testBeanTypeAndMethodExpression() throws Exception {
        Expression exp = BeanLanguage.bean(MyUser.class, "hello");
        Exchange exchange = createExchangeWithBody("Claus");

        Object result = exp.evaluate(exchange, Object.class);
        assertEquals("Hello Claus", result);
    }

    public void testBeanInstanceAndMethodExpression() throws Exception {
        MyUser user = new MyUser();
        Expression exp = BeanLanguage.bean(user, "hello");
        Exchange exchange = createExchangeWithBody("Claus");

        Object result = exp.evaluate(exchange, Object.class);
        assertEquals("Hello Claus", result);
    }

    public void testNoMethod() throws Exception {
        MyUser user = new MyUser();
        Expression exp = BeanLanguage.bean(user, "unknown");
        Exchange exchange = createExchangeWithBody("Claus");

        Object result = exp.evaluate(exchange, Object.class);
        assertNull(result);
        assertNotNull(exchange.getException());
        MethodNotFoundException e = assertIsInstanceOf(MethodNotFoundException.class, exchange.getException());
        assertSame(user, e.getBean());
        assertEquals("unknown", e.getMethodName());
    }

    public void testNoMethodBeanLookup() throws Exception {
        Expression exp = BeanLanguage.bean("foo.cake");
        Exchange exchange = createExchangeWithBody("Claus");

        Object result = exp.evaluate(exchange, Object.class);
        assertNull(result);
        assertNotNull(exchange.getException());
        MethodNotFoundException e = assertIsInstanceOf(MethodNotFoundException.class, exchange.getException());
        assertSame(context.getRegistry().lookupByName("foo"), e.getBean());
        assertEquals("cake", e.getMethodName());
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

        public boolean isFooHeaderAbc(@Header("foo") String foo) {
            return "abc".equals(foo);
        }
    }

    public static class MyUser {
        public String hello(String name) {
            return "Hello " + name;
        }
    }
}