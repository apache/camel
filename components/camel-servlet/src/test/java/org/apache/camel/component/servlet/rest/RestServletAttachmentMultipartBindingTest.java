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
package org.apache.camel.component.servlet.rest;

import java.io.InputStream;

import jakarta.activation.DataHandler;
import jakarta.servlet.MultipartConfigElement;

import io.undertow.servlet.api.DeploymentInfo;
import org.apache.camel.Exchange;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.servlet.ServletCamelRouterTestSupport;
import org.apache.camel.component.servlet.ServletEndpoint;
import org.apache.camel.component.servlet.ServletRestHttpBinding;
import org.apache.camel.http.common.HttpBinding;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RestServletAttachmentMultipartBindingTest extends ServletCamelRouterTestSupport {

    @Override
    protected DeploymentInfo getDeploymentInfo() {
        DeploymentInfo deploymentInfo = super.getDeploymentInfo();
        deploymentInfo.setDefaultMultipartConfig(new MultipartConfigElement(System.getProperty("java.io.tmpdir")));
        return deploymentInfo;
    }

    @Test
    void testUploadIsBoundAsAttachment() throws Exception {
        InputStream body = context.getTypeConverter().convertTo(InputStream.class, "Hello World");
        PostMethodWebRequest request = new PostMethodWebRequest(
                contextUrl + "/services/users/123/upload", body, "multipart/form-data; boundary=----Boundary");
        WebResponse response = query(request);

        assertEquals(200, response.getResponseCode());
        // the path placeholder is mapped to a header, and the uploaded part is an attachment
        assertEquals("123:Hello World", response.getText());
    }

    @Test
    void testRestBindingHasTheEndpointOptions() {
        ServletEndpoint endpoint = context.getEndpoints().stream()
                .filter(ServletEndpoint.class::isInstance)
                .map(ServletEndpoint.class::cast)
                .findFirst().orElseThrow();
        HttpBinding binding = endpoint.getHttpBinding();

        assertInstanceOf(ServletRestHttpBinding.class, binding);
        assertEquals("txt", binding.getFileNameExtWhitelist());
        assertTrue(binding.isLogException());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                restConfiguration().component("servlet")
                        .endpointProperty("attachmentMultipartBinding", "true")
                        .endpointProperty("fileNameExtWhitelist", "txt")
                        .endpointProperty("logException", "true");

                rest("/users/").post("{id}/upload").to("direct:upload");

                from("direct:upload").process(RestServletAttachmentMultipartBindingTest::replyWithAttachment);
            }
        };
    }

    private static void replyWithAttachment(Exchange exchange) throws Exception {
        DataHandler file = exchange.getMessage(AttachmentMessage.class).getAttachment("file");
        String content = file != null
                ? exchange.getContext().getTypeConverter().mandatoryConvertTo(String.class, file.getInputStream())
                : "no attachment";
        exchange.getMessage().setBody(exchange.getMessage().getHeader("id") + ":" + content);
    }
}
