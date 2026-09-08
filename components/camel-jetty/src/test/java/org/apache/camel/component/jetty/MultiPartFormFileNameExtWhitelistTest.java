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
package org.apache.camel.component.jetty;

import java.io.File;

import jakarta.activation.DataHandler;

import org.apache.camel.Exchange;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.builder.RouteBuilder;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The uploaded part carries the multipart field name {@code upload} and the submitted file name
 * {@code log4j2.properties}. The two differ, which is what tells the field name apart from the file name in both the
 * whitelist check and the header that exposes the attachment.
 */
public class MultiPartFormFileNameExtWhitelistTest extends BaseJettyTest {

    private static final String FIELD_NAME = "upload";
    private static final String FILE_NAME = "log4j2.properties";

    @Test
    public void testUploadAcceptedWhenExtensionIsWhitelisted() {
        // the attachment is keyed on the field name, and so is the header that exposes it. The client supplied
        // file name must not become a header name.
        assertThat(upload("/allowed")).isEqualTo("attachment=true,headerIsAttachment=true,fileNameHeader=false");
    }

    @Test
    public void testUploadRejectedWhenExtensionIsNotWhitelisted() {
        assertThat(upload("/rejected")).isEqualTo("attachment=false,headerIsAttachment=false,fileNameHeader=false");
    }

    @Test
    public void testUploadRejectedWhenExtensionIsOnlyASubstringOfTheWhitelist() {
        // "propertiesx".contains("properties") is true, but exact matching must reject the upload
        assertThat(upload("/substring")).isEqualTo("attachment=false,headerIsAttachment=false,fileNameHeader=false");
    }

    private String upload(String path) {
        File file = new File("src/test/resources/log4j2.properties");
        HttpEntity entity = MultipartEntityBuilder.create()
                .addBinaryBody(FIELD_NAME, file, ContentType.APPLICATION_OCTET_STREAM, FILE_NAME)
                .build();
        return template.requestBody("http://localhost:" + getPort() + path, entity, String.class);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                getContext().getGlobalOptions().put("CamelJettyTempDir", "target");

                from(whitelisted("/allowed", "properties"))
                        .process(MultiPartFormFileNameExtWhitelistTest::reportAttachment);
                from(whitelisted("/rejected", "pdf"))
                        .process(MultiPartFormFileNameExtWhitelistTest::reportAttachment);
                from(whitelisted("/substring", "propertiesx"))
                        .process(MultiPartFormFileNameExtWhitelistTest::reportAttachment);
            }

            private JettyHttpEndpoint whitelisted(String path, String whitelist) {
                // the whitelist is not exposed as a jetty endpoint option, it is configured on the binding
                JettyHttpEndpoint endpoint = getContext().getEndpoint(
                        "jetty://http://localhost:" + getPort() + path, JettyHttpEndpoint.class);
                endpoint.getHttpBinding().setFileNameExtWhitelist(whitelist);
                return endpoint;
            }
        };
    }

    private static void reportAttachment(Exchange exchange) {
        AttachmentMessage in = exchange.getIn(AttachmentMessage.class);
        DataHandler attachment = in.getAttachment(FIELD_NAME);
        Object headerByField = in.getHeader(FIELD_NAME);
        exchange.getMessage().setBody("attachment=" + (attachment != null)
                                      + ",headerIsAttachment=" + (attachment != null && headerByField == attachment)
                                      + ",fileNameHeader=" + (in.getHeader(FILE_NAME) != null));
    }
}
