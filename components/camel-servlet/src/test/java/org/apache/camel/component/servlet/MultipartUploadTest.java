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

import jakarta.activation.DataHandler;
import jakarta.servlet.MultipartConfigElement;

import io.undertow.servlet.api.DeploymentInfo;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MultipartUploadTest extends ServletCamelRouterTestSupport {

    @Override
    protected DeploymentInfo getDeploymentInfo() {
        DeploymentInfo deploymentInfo = super.getDeploymentInfo();
        String tmpDir = System.getProperty("java.io.tmpdir");
        MultipartConfigElement defaultMultipartConfig = new MultipartConfigElement(tmpDir);
        deploymentInfo.setDefaultMultipartConfig(defaultMultipartConfig);
        return deploymentInfo;
    }

    @Test
    void testMultipartUpload() throws IOException {
        String content = "Hello World";
        InputStream inputStream = context.getTypeConverter().convertTo(InputStream.class, content);
        PostMethodWebRequest request
                = new PostMethodWebRequest(
                        contextUrl + "/services/multipartUpload", inputStream, "multipart/form-data; boundary=----Boundary");
        WebResponse response = query(request);
        assertEquals(content, response.getText());
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("servlet:multipartUpload?attachmentMultipartBinding=true")
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                AttachmentMessage message = exchange.getMessage(AttachmentMessage.class);
                                DataHandler file = message.getAttachment("file");
                                if (file != null) {
                                    exchange.getMessage().setBody(file.getContent());
                                } else {
                                    exchange.getMessage().setBody(null);
                                }
                            }
                        });
            }
        };
    }
}
