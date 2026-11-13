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
package org.apache.camel.component.docling;

import ai.docling.serve.api.convert.request.options.OutputFormat;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests the format the docling-serve request asks for, for each documented value of the {@code outputFormat} option.
 * <p>
 * docling-serve fills only the content fields of the formats it was asked for, and it has no field for the split pages:
 * {@code html_split_page} was asked of the service, the response carried no content at all, and the body came out
 * empty. It now fails with the reason instead, and the five values the service can return still map to the format that
 * fills their field.
 */
class DoclingServeOutputFormatTest extends CamelTestSupport {

    @Test
    void htmlSplitPageFailsWithTheReasonInsteadOfAnEmptyBody() {
        assertThatThrownBy(() -> toFormat("html_split_page"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("html_split_page")
                .hasMessageContaining("docling-serve")
                .hasMessageContaining("outputFormat=html");
    }

    @Test
    void htmlSplitPageIsRejectedInUpperCaseToo() {
        assertThatThrownBy(() -> toFormat("HTML_SPLIT_PAGE"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theFormatsDoclingServeCanReturnAreStillRequested() throws Exception {
        assertThat(toFormat("markdown")).isEqualTo(OutputFormat.MARKDOWN);
        assertThat(toFormat("md")).isEqualTo(OutputFormat.MARKDOWN);
        assertThat(toFormat("html")).isEqualTo(OutputFormat.HTML);
        assertThat(toFormat("json")).isEqualTo(OutputFormat.JSON);
        assertThat(toFormat("text")).isEqualTo(OutputFormat.TEXT);
        assertThat(toFormat("txt")).isEqualTo(OutputFormat.TEXT);
        assertThat(toFormat("doctags")).isEqualTo(OutputFormat.DOCTAGS);
    }

    private OutputFormat toFormat(String outputFormat) throws Exception {
        DoclingEndpoint endpoint
                = context.getEndpoint("docling:convert?operation=CONVERT_TO_MARKDOWN", DoclingEndpoint.class);
        DoclingProducer producer = (DoclingProducer) endpoint.createProducer();

        return producer.mapToOutputFormat(outputFormat);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:noop").to("mock:noop");
            }
        };
    }
}
