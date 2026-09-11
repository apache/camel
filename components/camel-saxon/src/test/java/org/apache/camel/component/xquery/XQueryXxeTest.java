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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.xml.StringSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Verifies that {@link XQueryBuilder} does not resolve XML external entities when the message body already arrives as a
 * {@link javax.xml.transform.Source} (which bypasses Camel's hardened SAX/StAX type converters and is handed straight
 * to Saxon).
 */
public class XQueryXxeTest extends CamelTestSupport {

    private static final String SECRET = "CANARY-XQUERY-XXE-do-not-disclose";

    @TempDir
    Path tempDir;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testExternalEntityIsNotResolvedForSourceBody() throws Exception {
        Path secret = Files.writeString(tempDir.resolve("secret.txt"), SECRET);

        String payload = "<?xml version=\"1.0\"?>\n"
                         + "<!DOCTYPE data [ <!ENTITY xxe SYSTEM \"" + secret.toUri() + "\"> ]>\n"
                         + "<order status=\"pending\">&xxe;</order>";

        Exchange exchange = new DefaultExchange(context);
        // a Source-typed body is returned unchanged by getSource() and parsed directly by Saxon
        exchange.getIn().setBody(new StringSource(payload));

        XQueryBuilder xquery = XQueryBuilder.xquery("//order").asString();
        xquery.init(context);

        String outcome;
        try {
            outcome = String.valueOf(xquery.evaluate(exchange));
        } catch (Exception e) {
            outcome = stackTraceOf(e);
        }

        // The hardened Configuration must fail closed on the DOCTYPE (or at least never resolve the external
        // entity); either way the contents of the local file must not leak into the result or the error.
        assertFalse(outcome.contains(SECRET), "External entity was resolved - local file content leaked");
    }

    @Test
    public void testBenignSourceBodyStillEvaluates() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(new StringSource("<order status=\"pending\">hello-world</order>"));

        XQueryBuilder xquery = XQueryBuilder.xquery("//order").asString();
        xquery.init(context);

        String result = xquery.evaluate(exchange, String.class);
        assertEquals("hello-world", result);
    }

    private static String stackTraceOf(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
