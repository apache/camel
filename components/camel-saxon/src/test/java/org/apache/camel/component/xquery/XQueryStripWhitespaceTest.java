/*
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
package org.apache.camel.component.xquery;

import java.io.File;

import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class XQueryStripWhitespaceTest extends CamelTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testStripAll() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(new File("src/test/resources/payload.xml"));

        XQueryBuilder xquery = XQueryBuilder.xquery("//payload").asString().stripsAllWhiteSpace();
        Object result = xquery.evaluate(exchange);
        assertNotNull(result);
        assertEquals("012010-10-04JohnDoeThis is a test reportserver is downsomeone@somewhere.com12345678", result);
    }

    @Test
    public void testStripIgnorable() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(new File("src/test/resources/payload.xml"));

        XQueryBuilder xquery = XQueryBuilder.xquery("//payload").asString().stripsIgnorableWhiteSpace();
        String result = xquery.evaluate(exchange, String.class);
        assertNotNull(result);

        // replace double spaces to make equals easier
        result = result.replaceAll("\\s{2,}", " ");

        assertEquals(" 01 2010-10-04 John Doe This is a test report server is down someone@somewhere.com 12345678 ", result);
    }

}
