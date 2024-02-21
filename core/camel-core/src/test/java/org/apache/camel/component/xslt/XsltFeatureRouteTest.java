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
package org.apache.camel.component.xslt;

import javax.xml.transform.TransformerException;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class XsltFeatureRouteTest extends ContextTestSupport {

    @Test
    public void testSendMessage() throws Exception {
        String message = "<hello/>";
        sendXmlMessage("direct:start1", message);
        sendXmlMessage("direct:start2", message);
    }

    public void sendXmlMessage(String uri, String message) {
        Exception ex = assertThrows(Exception.class, () -> template.sendBody("direct:start1", message),
                "Expected an exception here");

        // expect an exception here
        boolean b1 = ex instanceof CamelExecutionException;
        assertTrue(b1, "Get a wrong exception");
        boolean b = ex.getCause() instanceof TransformerException;
        assertTrue(b, "Get a wrong exception cause");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start1").to("xslt:org/apache/camel/component/xslt/transform_text_imported.xsl").to("mock:result");

                from("direct:start2").to("xslt:org/apache/camel/component/xslt/transform_text.xsl").to("mock:result");
            }
        };
    }

}
