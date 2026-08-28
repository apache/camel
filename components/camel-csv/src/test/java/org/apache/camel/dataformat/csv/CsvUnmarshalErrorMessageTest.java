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
package org.apache.camel.dataformat.csv;

import java.io.IOException;
import java.util.List;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that {@link CsvUnmarshaller} wraps Commons CSV parse errors with a diagnostic message that guides operators
 * toward the fix.
 */
public class CsvUnmarshalErrorMessageTest extends CamelTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:bulk")
                        .unmarshal(new CsvDataFormat());
            }
        };
    }

    @Test
    void testEncapsulatedTokenErrorBulkProducesDiagnosticMessage() {
        // "OrderId"x,"Name" — extra char after closing quote before delimiter
        String badCsv = "\"OrderId\"x,\"Name\"\n1,Alice\n";

        assertThatThrownBy(() -> template.requestBody("direct:bulk", badCsv, List.class))
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isInstanceOf(IOException.class)
                .hasMessageContaining("CSV parse failed")
                .hasMessageContaining("encapsulated token")
                .hasMessageContaining("quoted field has extra characters")
                .hasMessageContaining("delimiter");
    }

    @Test
    void testEncapsulatedTokenErrorMessageContainsOriginalCause() {
        String badCsv = "\"OrderId\"x,\"Name\"\n1,Alice\n";

        assertThatThrownBy(() -> template.requestBody("direct:bulk", badCsv, List.class))
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isInstanceOf(IOException.class)
                .cause()
                .isNotNull()
                .hasMessageContaining("encapsulated token");
    }

    @Test
    void testValidCsvPassesThroughBulk() throws Exception {
        String validCsv = "Alice,30\nBob,25\n";

        List<?> result = template.requestBody("direct:bulk", validCsv, List.class);

        assertThat(result).hasSize(2);
    }
}
