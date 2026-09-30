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
package org.apache.camel.dsl.jbang.core.commands.ai;

import org.junit.jupiter.api.Test;

import static org.apache.camel.dsl.jbang.core.commands.ai.ProjectOverviewTest.CATALOG;
import static org.assertj.core.api.Assertions.assertThat;

class CatalogEndpointDslResolverTest {

    private final CatalogEndpointDslResolver resolver = new CatalogEndpointDslResolver(CATALOG);

    @Test
    void theHeaderNamesOfTheEndpointDsl() {
        // CAMEL-25204: checked against every header method the endpoint DSL generates, 3456 of them
        assertThat(resolver.headerName("file", "fileName")).isEqualTo("CamelFileName");
        assertThat(resolver.headerName("kafka", "kafkaKey")).isEqualTo("CamelKafkaKey");
        // a header name with dots or dashes, and a component whose factory differs from its scheme
        assertThat(resolver.headerName("cxfrs", "orgApacheCxfMessageMessageProtocolHeaders"))
                .isEqualTo("org.apache.cxf.message.Message.PROTOCOL_HEADERS");
        assertThat(resolver.headerName("imap", "replyTo")).isEqualTo("Reply-To");
        assertThat(resolver.headerName("aws2S3", "awsS3Key")).isEqualTo("CamelAwsS3Key");
        // not a header, not a component
        assertThat(resolver.headerName("kafka", "noSuchHeader")).isNull();
        assertThat(resolver.headerName("noSuchComponent", "fileName")).isNull();
    }

    @Test
    void theMethodOfAHeaderAsTheEndpointDslIsGenerated() {
        assertThat(CatalogEndpointDslResolver.headerMethod("CamelFileName")).isEqualTo("fileName");
        assertThat(CatalogEndpointDslResolver.headerMethod("Content-Type")).isEqualTo("contentType");
        assertThat(CatalogEndpointDslResolver.headerMethod("CamelBox.")).isEqualTo("box");
        assertThat(CatalogEndpointDslResolver.headerMethod("kafka.KEY")).isEqualTo("kafkaKey");
    }
}
