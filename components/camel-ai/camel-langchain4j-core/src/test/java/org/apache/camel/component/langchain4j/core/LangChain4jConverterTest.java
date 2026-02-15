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
package org.apache.camel.component.langchain4j.core;

import dev.langchain4j.data.document.Document;
import org.apache.camel.Exchange;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LangChain4jConverterTest extends CamelTestSupport {

    @Test
    public void testConverter() throws NoTypeConversionAvailableException {
        Document doc = context.getTypeConverter().mandatoryConvertTo(Document.class, "foo");
        assertThat(doc.text()).isEqualTo("foo");

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(LangChain4j.METADATA_PREFIX + "meta1", 1);
        exchange.getMessage().setHeader(LangChain4j.METADATA_PREFIX + "meta2", "baz");
        exchange.getMessage().setHeader("meta3", "ignored");

        Document docWithMetadata = context.getTypeConverter().mandatoryConvertTo(
                Document.class,
                exchange,
                "bar");

        assertThat(docWithMetadata.text()).isEqualTo("bar");
        assertThat(docWithMetadata.metadata().toMap())
                .containsEntry("meta1", 1)
                .containsEntry("meta2", "baz")
                .doesNotContainKey("meta3");
    }
}
