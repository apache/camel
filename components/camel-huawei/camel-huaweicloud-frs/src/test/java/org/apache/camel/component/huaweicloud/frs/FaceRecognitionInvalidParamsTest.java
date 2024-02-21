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
package org.apache.camel.component.huaweicloud.frs;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.huaweicloud.frs.constants.FaceRecognitionProperties;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class FaceRecognitionInvalidParamsTest extends CamelTestSupport {
    private final TestConfiguration testConfiguration = new TestConfiguration();

    private final String testFilePath = "test_file";

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:access_key_not_set")
                        .to("hwcloud-frs:faceDetection?"
                            + "secretKey=" + testConfiguration.getProperty("secretKey")
                            + "&projectId=" + testConfiguration.getProperty("projectId")
                            + "&region=" + testConfiguration.getProperty("region")
                            + "&ignoreSslVerification=true")
                        .to("mock:result");

                from("direct:secret_key_not_set")
                        .to("hwcloud-frs:faceDetection?"
                            + "accessKey=" + testConfiguration.getProperty("accessKey")
                            + "&projectId=" + testConfiguration.getProperty("projectId")
                            + "&region=" + testConfiguration.getProperty("region")
                            + "&ignoreSslVerification=true")
                        .to("mock:result");

                from("direct:project_id_not_set")
                        .to("hwcloud-frs:faceDetection?"
                            + "accessKey=" + testConfiguration.getProperty("accessKey")
                            + "&secretKey=" + testConfiguration.getProperty("secretKey")
                            + "&region=" + testConfiguration.getProperty("region")
                            + "&ignoreSslVerification=true")
                        .to("mock:result");

                from("direct:region_id_not_set")
                        .to("hwcloud-frs:faceDetection?"
                            + "accessKey=" + testConfiguration.getProperty("accessKey")
                            + "&secretKey=" + testConfiguration.getProperty("secretKey")
                            + "&projectId=" + testConfiguration.getProperty("projectId")
                            + "&ignoreSslVerification=true")
                        .to("mock:result");

                from("direct:operation_not_set")
                        .to("hwcloud-frs:?"
                            + "accessKey=" + testConfiguration.getProperty("accessKey")
                            + "&secretKey=" + testConfiguration.getProperty("secretKey")
                            + "&projectId=" + testConfiguration.getProperty("projectId")
                            + "&region=" + testConfiguration.getProperty("region")
                            + "&ignoreSslVerification=true")
                        .to("mock:result");

                from("direct:operation_invalid")
                        .to("hwcloud-frs:test?"
                            + "accessKey=" + testConfiguration.getProperty("accessKey")
                            + "&secretKey=" + testConfiguration.getProperty("secretKey")
                            + "&projectId=" + testConfiguration.getProperty("projectId")
                            + "&region=" + testConfiguration.getProperty("region")
                            + "&ignoreSslVerification=true")
                        .to("mock:result");

                from("direct:face_detection_image_not_set")
                        .to("hwcloud-frs:faceDetection?"
                            + "accessKey=" + testConfiguration.getProperty("accessKey")
                            + "&secretKey=" + testConfiguration.getProperty("secretKey")
                            + "&projectId=" + testConfiguration.getProperty("projectId")
                            + "&region=" + testConfiguration.getProperty("region")
                            + "&ignoreSslVerification=true")
                        .to("mock:result");

                from("direct:face_detection_image_file_not_found")
                        .setProperty(FaceRecognitionProperties.FACE_IMAGE_FILE_PATH, constant(testFilePath))
                        .to("hwcloud-frs:faceDetection?"
                            + "accessKey=" + testConfiguration.getProperty("accessKey")
                            + "&secretKey=" + testConfiguration.getProperty("secretKey")
                            + "&projectId=" + testConfiguration.getProperty("projectId")
                            + "&region=" + testConfiguration.getProperty("region")
                            + "&ignoreSslVerification=true")
                        .to("mock:result");

                from("direct:face_verification_image_not_set")
                        .to("hwcloud-frs:faceVerification?"
                            + "accessKey=" + testConfiguration.getProperty("accessKey")
                            + "&secretKey=" + testConfiguration.getProperty("secretKey")
                            + "&projectId=" + testConfiguration.getProperty("projectId")
                            + "&region=" + testConfiguration.getProperty("region")
                            + "&ignoreSslVerification=true")
                        .to("mock:result");

                from("direct:face_verification_only_one_image_set")
                        .setProperty(FaceRecognitionProperties.FACE_IMAGE_FILE_PATH,
                                constant(testConfiguration.getProperty("imageFilePath")))
                        .to("hwcloud-frs:faceVerification?"
                            + "accessKey=" + testConfiguration.getProperty("accessKey")
                            + "&secretKey=" + testConfiguration.getProperty("secretKey")
                            + "&projectId=" + testConfiguration.getProperty("projectId")
                            + "&region=" + testConfiguration.getProperty("region")
                            + "&ignoreSslVerification=true")
                        .to("mock:result");

                from("direct:face_verification_image_sources_not_match")
                        .setProperty(FaceRecognitionProperties.FACE_IMAGE_BASE64,
                                constant(testConfiguration.getProperty("imageBase64")))
                        .setProperty(FaceRecognitionProperties.ANOTHER_FACE_IMAGE_URL,
                                constant(testConfiguration.getProperty("imageFilePath")))
                        .to("hwcloud-frs:faceVerification?"
                            + "accessKey=" + testConfiguration.getProperty("accessKey")
                            + "&secretKey=" + testConfiguration.getProperty("secretKey")
                            + "&projectId=" + testConfiguration.getProperty("projectId")
                            + "&region=" + testConfiguration.getProperty("region")
                            + "&ignoreSslVerification=true")
                        .to("mock:result");

                from("direct:face_verification_image_file_not_found")
                        .setProperty(FaceRecognitionProperties.FACE_IMAGE_FILE_PATH, constant(testFilePath))
                        .setProperty(FaceRecognitionProperties.ANOTHER_FACE_IMAGE_FILE_PATH,
                                constant(testConfiguration.getProperty("imageFilePath")))
                        .to("hwcloud-frs:faceVerification?"
                            + "accessKey=" + testConfiguration.getProperty("accessKey")
                            + "&secretKey=" + testConfiguration.getProperty("secretKey")
                            + "&projectId=" + testConfiguration.getProperty("projectId")
                            + "&region=" + testConfiguration.getProperty("region")
                            + "&ignoreSslVerification=true")
                        .to("mock:result");

                from("direct:face_live_detection_video_not_set")
                        .to("hwcloud-frs:faceLiveDetection?"
                            + "accessKey=" + testConfiguration.getProperty("accessKey")
                            + "&secretKey=" + testConfiguration.getProperty("secretKey")
                            + "&projectId=" + testConfiguration.getProperty("projectId")
                            + "&region=" + testConfiguration.getProperty("region")
                            + "&actions=1,2,3"
                            + "&ignoreSslVerification=true")
                        .to("mock:result");

                from("direct:face_live_detection_video_file_not_found")
                        .setProperty(FaceRecognitionProperties.FACE_VIDEO_FILE_PATH, constant(testFilePath))
                        .to("hwcloud-frs:faceLiveDetection?"
                            + "accessKey=" + testConfiguration.getProperty("accessKey")
                            + "&secretKey=" + testConfiguration.getProperty("secretKey")
                            + "&projectId=" + testConfiguration.getProperty("projectId")
                            + "&region=" + testConfiguration.getProperty("region")
                            + "&actions=1,2,3"
                            + "&ignoreSslVerification=true")
                        .to("mock:result");

                from("direct:face_live_detection_actions_not_set")
                        .setProperty(FaceRecognitionProperties.FACE_VIDEO_FILE_PATH,
                                constant(testConfiguration.getProperty("videoFilePath")))
                        .to("hwcloud-frs:faceLiveDetection?"
                            + "accessKey=" + testConfiguration.getProperty("accessKey")
                            + "&secretKey=" + testConfiguration.getProperty("secretKey")
                            + "&projectId=" + testConfiguration.getProperty("projectId")
                            + "&region=" + testConfiguration.getProperty("region")
                            + "&ignoreSslVerification=true")
                        .to("mock:result");
            }
        };
    }

    /**
     * access key is not set
     */
    @Test
    public void testAccessKeyNotSet() {
        Exception exception
                = assertThrows(CamelExecutionException.class, () -> template.sendBody("direct:access_key_not_set", ""));
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
        String expectedMessage = "authentication parameter access key (AK) not found";
        assertEquals(exception.getCause().getMessage(), expectedMessage);
    }

    /**
     * secret key is not set
     */
    @Test
    public void testSecretKeyNotSet() {
        Exception exception
                = assertThrows(CamelExecutionException.class, () -> template.sendBody("direct:secret_key_not_set", ""));
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
        String expectedMessage = "authentication parameter secret key (SK) not found";
        assertEquals(exception.getCause().getMessage(), expectedMessage);
    }

    /**
     * project id is not set
     */
    @Test
    public void testProjectIdNotSet() {
        Exception exception
                = assertThrows(CamelExecutionException.class, () -> template.sendBody("direct:project_id_not_set", ""));
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
        String expectedMessage = "Project id not found";
        assertEquals(exception.getCause().getMessage(), expectedMessage);
    }

    /**
     * region id or endpoint is not set
     */
    @Test
    public void testRegionIdNotSet() {
        Exception exception
                = assertThrows(CamelExecutionException.class, () -> template.sendBody("direct:region_id_not_set", ""));
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
        String expectedMessage = "either endpoint or region needs to be set";
        assertEquals(exception.getCause().getMessage(), expectedMessage);
    }

    /**
     * operation is not set
     */
    @Test
    public void testOperationNotSet() {
        Exception exception
                = assertThrows(CamelExecutionException.class, () -> template.sendBody("direct:operation_not_set", ""));
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
        String expectedMessage = "operation needs to be set";
        assertEquals(exception.getCause().getMessage(), expectedMessage);
    }

    /**
     * operation is invalid
     */
    @Test
    public void testOperationInvalid() {
        Exception exception
                = assertThrows(CamelExecutionException.class, () -> template.sendBody("direct:operation_invalid", ""));
        assertTrue(exception.getCause() instanceof UnsupportedOperationException);
        String expectedMessage = "operation needs to be faceDetection, faceVerification or faceLiveDetection";
        assertEquals(exception.getCause().getMessage(), expectedMessage);
    }

    /**
     * image base64, url or filePath is not set when operation is faceDetection
     */
    @Test
    public void testFaceDetectionImageNotSet() {
        Exception exception = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:face_detection_image_not_set", ""));
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
        String expectedMessage = "any one of image base64, url and filePath needs to be set";
        assertEquals(exception.getCause().getMessage(), expectedMessage);
    }

    /**
     * image filePath is invalid when operation is faceDetection
     */
    @Test
    public void testFaceDetectionImageFileInvalid() {
        Exception exception = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:face_detection_image_file_not_found", ""));
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
        String expectedMessage = String.format("Image file path is invalid: %s", testFilePath);
        assertEquals(exception.getCause().getMessage(), expectedMessage);
    }

    /**
     * image base64, url or filePath is not set when operation is faceVerification
     */
    @Test
    public void testFaceVerificationImageNotSet() {
        Exception exception = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:face_verification_image_not_set", ""));
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
        String expectedMessage = "any one of image base64, url and filePath needs to be set";
        assertEquals(exception.getCause().getMessage(), expectedMessage);
    }

    /**
     * only one image source is set when operation is faceVerification
     */
    @Test
    public void testFaceVerificationOnlyOneImageSet() {
        Exception exception = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:face_verification_only_one_image_set", ""));
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
        String expectedMessage = "any one of image base64, url and filePath needs to be set";
        assertEquals(exception.getCause().getMessage(), expectedMessage);
    }

    /**
     * sources of two images do not match when operation is faceVerification If imageBase64 is configured, then
     * anotherImageBase64 needs to be configured If imageUrl is configured, then anotherImageUrl needs to be configured
     * If imageFilePath is configured, then anotherImageFilePath needs to be configured
     */
    @Test
    public void testFaceVerificationImageSourcesNotMatch() {
        Exception exception = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:face_verification_image_sources_not_match", ""));
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
        String expectedMessage = "any one of image base64, url and filePath needs to be set";
        assertEquals(exception.getCause().getMessage(), expectedMessage);
    }

    /**
     * image filePath is invalid when operation is faceVerification
     */
    @Test
    public void testFaceVerificationImageFileInvalid() {
        Exception exception = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:face_verification_image_file_not_found", ""));
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
        String expectedMessage = String.format("Image file paths are invalid: %s, %s", testFilePath,
                testConfiguration.getProperty("imageFilePath"));
        assertEquals(exception.getCause().getMessage(), expectedMessage);
    }

    /**
     * video base64, url or filePath is not set when operation is faceLiveDetection
     */
    @Test
    public void testFaceLiveDetectionVideoNotSet() {
        Exception exception = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:face_live_detection_video_not_set", ""));
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
        String expectedMessage = "any one of video base64, url and filePath needs to be set";
        assertEquals(exception.getCause().getMessage(), expectedMessage);
    }

    /**
     * video filePath is invalid when operation is faceLiveDetection
     */
    @Test
    public void testFaceLiveDetectionVideoFileInvalid() {
        Exception exception = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:face_live_detection_video_file_not_found", ""));
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
        String expectedMessage = String.format("Video file path is invalid: %s", testFilePath);
        assertEquals(exception.getCause().getMessage(), expectedMessage);
    }

    /**
     * actions are not set when operation is faceLiveDetection
     */
    @Test
    public void testFaceLiveDetectionVideoActionsNotSet() {
        Exception exception = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:face_live_detection_actions_not_set", ""));
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
        String expectedMessage = "actions needs to be set";
        assertEquals(exception.getCause().getMessage(), expectedMessage);
    }
}
