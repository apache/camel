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
package org.apache.camel.language.ognl;

import org.apache.camel.LanguageTestSupport;

/**
 * @version $Revision$
 */
public class OgnlTest extends LanguageTestSupport {
    public void testOgnlExpressions() throws Exception {
        assertExpression("exchange", exchange);
        assertExpression("exchange.getIn().body", "<hello id='m123'>world!</hello>");
        assertExpression("getIn().body", "<hello id='m123'>world!</hello>");
        assertExpression("request.body", "<hello id='m123'>world!</hello>");
        assertExpression("getIn().headers['foo']", "abc");
        assertExpression("getIn().headers.foo", "abc");
        assertExpression("request.headers.foo", "abc");
    }

    public void testGetOutFalseKeepsNullOutMessage() throws Exception {
        assertExpression("exchange.getOut(false)", null);
        assertNull(exchange.getOut(false));
    }

    public void testResponseCreatesOutMessage() throws Exception {
        assertExpression("response.body", null);
        assertNotNull(exchange.getOut(false));
    }

    protected String getLanguageName() {
        return "ognl";
    }
}
