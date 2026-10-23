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
package org.apache.camel.component.aws2.lambda;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.lambda.model.FunctionCode;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionCodeRequest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LambdaProducerCodeAndPayloadTest extends CamelTestSupport {

    private static final byte[] BODY_BYTES = new byte[] { 1, 2, 3 };

    @BindToRegistry("awsLambdaClient")
    AmazonLambdaClientMock clientMock = new AmazonLambdaClientMock();

    private static String zipFilePath() {
        return new File(
                LambdaProducerCodeAndPayloadTest.class.getClassLoader()
                        .getResource("org/apache/camel/component/aws2/lambda/function/node/GetHelloWithName.zip").getFile())
                .getAbsolutePath();
    }

    private static Map<String, Object> createHeaders() {
        Map<String, Object> headers = new HashMap<>();
        headers.put(Lambda2Constants.RUNTIME, "nodejs22.x");
        headers.put(Lambda2Constants.HANDLER, "GetHelloWithName.handler");
        headers.put(Lambda2Constants.ROLE, "arn:aws:iam::643534317684:role/lambda-execution-role");
        return headers;
    }

    private static IllegalArgumentException assertFailsWith(Runnable send) {
        CamelExecutionException e = assertThrows(CamelExecutionException.class, send::run);
        return assertInstanceOf(IllegalArgumentException.class, e.getCause());
    }

    // invokeFunction

    @Test
    public void invokeWithoutBodySendsNoPayload() {
        template.sendBody("direct:invokeFunction", null);

        assertNull(clientMock.invokeRequest.payload());
    }

    @Test
    public void invokeEncodesTextAsUtf8() {
        String body = "{\"name\":\"Café\"}";
        template.sendBody("direct:invokeFunction", body);

        assertArrayEquals(body.getBytes(StandardCharsets.UTF_8), clientMock.invokeRequest.payload().asByteArray());
    }

    @Test
    public void invokeSendsBytesAsTheyAre() {
        byte[] body = new byte[] { (byte) 0xFF, (byte) 0xFE, 0x41 };
        template.sendBody("direct:invokeFunction", body);

        assertArrayEquals(body, clientMock.invokeRequest.payload().asByteArray());
    }

    @Test
    public void invokeSendsStreamAsItIs() {
        byte[] body = new byte[] { (byte) 0xFF, (byte) 0xFE, 0x41 };
        template.sendBody("direct:invokeFunction", new ByteArrayInputStream(body));

        assertArrayEquals(body, clientMock.invokeRequest.payload().asByteArray());
    }

    // createFunction

    @Test
    public void createWithMapBodyFails() {
        IllegalArgumentException e = assertFailsWith(
                () -> template.sendBodyAndHeaders("direct:createFunction", Map.of("a", "b"), createHeaders()));
        assertTrue(e.getMessage().contains("must be the zip of the code as bytes, a stream or a file"), e.getMessage());
        assertTrue(e.getMessage().contains(Lambda2Constants.ZIP_FILE), e.getMessage());
    }

    @Test
    public void createZipFileHeaderWinsOverBody() throws Exception {
        Map<String, Object> headers = createHeaders();
        headers.put(Lambda2Constants.ZIP_FILE, zipFilePath());
        template.sendBodyAndHeaders("direct:createFunction", BODY_BYTES, headers);

        FunctionCode code = clientMock.createFunctionRequest.code();
        assertArrayEquals(Files.readAllBytes(new File(zipFilePath()).toPath()), code.zipFile().asByteArray());
        assertNull(code.s3Bucket());
    }

    @Test
    public void createS3LocationWinsOverBody() {
        Map<String, Object> headers = createHeaders();
        headers.put(Lambda2Constants.S3_BUCKET, "mybucket");
        headers.put(Lambda2Constants.S3_KEY, "function.zip");
        template.sendBodyAndHeaders("direct:createFunction", BODY_BYTES, headers);

        FunctionCode code = clientMock.createFunctionRequest.code();
        assertNull(code.zipFile());
        assertEquals("mybucket", code.s3Bucket());
        assertEquals("function.zip", code.s3Key());
    }

    @Test
    public void createWithBytesBody() {
        template.sendBodyAndHeaders("direct:createFunction", BODY_BYTES, createHeaders());

        FunctionCode code = clientMock.createFunctionRequest.code();
        assertArrayEquals(BODY_BYTES, code.zipFile().asByteArray());
        assertNull(code.s3Bucket());
    }

    @Test
    public void createZipFileAndS3LocationFails() {
        Map<String, Object> headers = createHeaders();
        headers.put(Lambda2Constants.ZIP_FILE, zipFilePath());
        headers.put(Lambda2Constants.S3_BUCKET, "mybucket");
        headers.put(Lambda2Constants.S3_KEY, "function.zip");
        IllegalArgumentException e
                = assertFailsWith(() -> template.sendBodyAndHeaders("direct:createFunction", null, headers));
        assertTrue(e.getMessage().contains("not both"), e.getMessage());
    }

    @Test
    public void createS3BucketWithoutKeyFails() {
        Map<String, Object> headers = createHeaders();
        headers.put(Lambda2Constants.S3_BUCKET, "mybucket");
        IllegalArgumentException e
                = assertFailsWith(() -> template.sendBodyAndHeaders("direct:createFunction", BODY_BYTES, headers));
        assertTrue(e.getMessage().contains(Lambda2Constants.S3_KEY), e.getMessage());
    }

    @Test
    public void createWithoutCodeFails() {
        IllegalArgumentException e
                = assertFailsWith(() -> template.sendBodyAndHeaders("direct:createFunction", null, createHeaders()));
        assertTrue(e.getMessage().contains(Lambda2Constants.ZIP_FILE), e.getMessage());
    }

    // updateFunction

    @Test
    public void updateWithMapBodyFails() {
        IllegalArgumentException e
                = assertFailsWith(() -> template.sendBody("direct:updateFunction", Map.of("a", "b")));
        assertTrue(e.getMessage().contains("must be the zip of the code as bytes, a stream or a file"), e.getMessage());
    }

    @Test
    public void updateZipFileHeaderWinsOverBody() throws Exception {
        template.sendBodyAndHeader("direct:updateFunction", BODY_BYTES, Lambda2Constants.ZIP_FILE, zipFilePath());

        UpdateFunctionCodeRequest request = clientMock.updateFunctionCodeRequest;
        assertArrayEquals(Files.readAllBytes(new File(zipFilePath()).toPath()), request.zipFile().asByteArray());
        assertNull(request.s3Bucket());
    }

    @Test
    public void updateS3LocationWinsOverBody() {
        Map<String, Object> headers = new HashMap<>();
        headers.put(Lambda2Constants.S3_BUCKET, "mybucket");
        headers.put(Lambda2Constants.S3_KEY, "function.zip");
        headers.put(Lambda2Constants.S3_OBJECT_VERSION, "2");
        template.sendBodyAndHeaders("direct:updateFunction", BODY_BYTES, headers);

        UpdateFunctionCodeRequest request = clientMock.updateFunctionCodeRequest;
        assertNull(request.zipFile());
        assertEquals("mybucket", request.s3Bucket());
        assertEquals("function.zip", request.s3Key());
        assertEquals("2", request.s3ObjectVersion());
    }

    @Test
    public void updateS3BucketWithoutKeyFails() {
        IllegalArgumentException e = assertFailsWith(
                () -> template.sendBodyAndHeader("direct:updateFunction", null, Lambda2Constants.S3_BUCKET, "mybucket"));
        assertTrue(e.getMessage().contains(Lambda2Constants.S3_KEY), e.getMessage());
    }

    @Test
    public void updateS3KeyWithoutBucketFails() {
        IllegalArgumentException e = assertFailsWith(
                () -> template.sendBodyAndHeader("direct:updateFunction", null, Lambda2Constants.S3_KEY, "function.zip"));
        assertTrue(e.getMessage().contains(Lambda2Constants.S3_BUCKET), e.getMessage());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:invokeFunction")
                        .to("aws2-lambda://GetHelloWithName?awsLambdaClient=#awsLambdaClient&operation=invokeFunction");
                from("direct:createFunction")
                        .to("aws2-lambda://GetHelloWithName?awsLambdaClient=#awsLambdaClient&operation=createFunction");
                from("direct:updateFunction")
                        .to("aws2-lambda://GetHelloWithName?awsLambdaClient=#awsLambdaClient&operation=updateFunction");
            }
        };
    }
}
