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
package org.apache.camel.component.servlet;

import java.io.IOException;
import java.io.InputStream;

import jakarta.servlet.MultipartConfigElement;

import io.undertow.servlet.api.DeploymentInfo;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The test harness posts a part whose multipart field name is {@code file} and whose submitted file name is
 * {@code test.txt}. The field name carries no extension, so checking the whitelist against it never rejects anything.
 */
public class MultipartUploadFileNameExtWhitelistTest extends ServletCamelRouterTestSupport {

    @Override
    protected DeploymentInfo getDeploymentInfo() {
        DeploymentInfo deploymentInfo = super.getDeploymentInfo();
        deploymentInfo.setDefaultMultipartConfig(new MultipartConfigElement(System.getProperty("java.io.tmpdir")));
        return deploymentInfo;
    }

    @Test
    void testUploadAcceptedWhenExtensionIsWhitelisted() throws IOException {
        assertEquals("accepted", upload("allowed"));
    }

    @Test
    void testUploadRejectedWhenExtensionIsNotWhitelisted() throws IOException {
        assertEquals("no attachment", upload("rejected"));
    }

    @Test
    void testUploadRejectedWhenExtensionIsOnlyASubstringOfTheWhitelist() throws IOException {
        // the whitelist "txtdoc" must not accept a "test.txt" upload just because "txtdoc".contains("txt")
        assertEquals("no attachment", upload("substring"));
    }

    private String upload(String path) throws IOException {
        InputStream body = context.getTypeConverter().convertTo(InputStream.class, "Hello World");
        PostMethodWebRequest request = new PostMethodWebRequest(
                contextUrl + "/services/" + path, body, "multipart/form-data; boundary=----Boundary");
        return query(request).getText();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("servlet:allowed?attachmentMultipartBinding=true&fileNameExtWhitelist=txt")
                        .process(MultipartUploadFileNameExtWhitelistTest::reportAttachment);

                from("servlet:rejected?attachmentMultipartBinding=true&fileNameExtWhitelist=pdf")
                        .process(MultipartUploadFileNameExtWhitelistTest::reportAttachment);

                from("servlet:substring?attachmentMultipartBinding=true&fileNameExtWhitelist=txtdoc")
                        .process(MultipartUploadFileNameExtWhitelistTest::reportAttachment);
            }
        };
    }

    private static void reportAttachment(Exchange exchange) {
        AttachmentMessage message = exchange.getMessage(AttachmentMessage.class);
        boolean present = message.getAttachment("file") != null;
        exchange.getMessage().setBody(present ? "accepted" : "no attachment");
    }
}
