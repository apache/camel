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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies that the FOP producer configures its {@code TransformerFactory} to not resolve external DTDs/stylesheets,
 * consistent with Camel's standard secure XML processing configuration.
 */
public class FopExternalEntityTest extends CamelTestSupport {

    @Test
    public void externalDtdIsNotResolved(@TempDir Path tempDir) throws IOException {
        // the referenced DTD exists and is perfectly readable, so the transformation would succeed if the
        // producer resolved it. The failure below therefore proves the external DTD was never fetched, without
        // depending on the wording of the JDK error message (which differs across JDK releases)
        Path dtd = tempDir.resolve("external.dtd");
        Files.writeString(dtd, "<!ELEMENT fo:root ANY>\n");

        String body = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                      + "<!DOCTYPE fo:root SYSTEM \"" + dtd.toUri() + "\">\n"
                      + FopHelper.decorateTextWithXSLFO("Hello");

        assertThrows(CamelExecutionException.class, () -> template.sendBody("direct:start", body));
    }

    @Test
    public void documentWithoutExternalDtdIsRendered() {
        // guards the test above from passing for the wrong reason: the very same document renders fine as long
        // as it does not point at an external DTD
        assertDoesNotThrow(() -> template.sendBody("direct:start", FopHelper.decorateTextWithXSLFO("Hello")));
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
