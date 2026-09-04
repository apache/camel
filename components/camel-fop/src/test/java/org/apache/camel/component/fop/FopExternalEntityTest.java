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
package org.apache.camel.component.fop;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the FOP producer configures its {@code TransformerFactory} to not resolve external DTDs/stylesheets,
 * consistent with Camel's standard secure XML processing configuration.
 */
public class FopExternalEntityTest extends CamelTestSupport {

    private static final String FO_WITH_EXTERNAL_DTD
            = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
              + "<!DOCTYPE fo:root SYSTEM \"file:///non-existent-external.dtd\">\n"
              + FopHelper.decorateTextWithXSLFO("Hello");

    @Test
    public void externalDtdIsNotResolved() {
        CamelExecutionException ex = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:start", FO_WITH_EXTERNAL_DTD));

        String messages = collectMessages(ex);
        assertTrue(messages.contains("accessExternalDTD"),
                "Transformation should be blocked by the accessExternalDTD restriction, but failed with: " + messages);
    }

    private static String collectMessages(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null) {
                sb.append(current.getMessage()).append('\n');
            }
            current = current.getCause();
        }
        return sb.toString();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .to("fop:pdf")
                        .to("mock:result");
            }
        };
    }
}
