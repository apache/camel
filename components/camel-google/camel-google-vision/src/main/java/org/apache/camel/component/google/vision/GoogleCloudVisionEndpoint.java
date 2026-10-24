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
package org.apache.camel.component.google.vision;

import com.google.cloud.vision.v1.ImageAnnotatorClient;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * Detect labels, text, faces, logos and more on images through Google Cloud Vision API
 */
@UriEndpoint(firstVersion = "4.19.0", scheme = "google-vision", title = "Google Cloud Vision",
             syntax = "google-vision:operation", category = {
                     Category.CLOUD, Category.AI },
             producerOnly = true, headersClass = GoogleCloudVisionConstants.class)
@BodyTypes(producerIn = @BodyType(value = { Carrier.BYTES, Carrier.STREAM, Carrier.FILE },
                                  byOption = @BodyType.When(option = "pojoRequest", value = "true", carriers = Carrier.POJO,
                                                            javaType = "com.google.cloud.vision.v1.AnnotateImageRequest"),
                                  description = "The image to annotate, sent inline. A stream or a file is read into bytes"
                                                + " and a String is sent as its bytes, a Map, a POJO or no body fails. With"
                                                + " pojoRequest=true an AnnotateImageRequest of the Google Cloud SDK: another"
                                                + " body fails"),
           producerOut = @BodyType(value = { Carrier.LIST, Carrier.POJO },
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "labelDetection",
                                                          carriers = Carrier.LIST,
                                                          javaType = "java.util.List<com.google.cloud.vision.v1.EntityAnnotation>"),
                                           @BodyType.When(option = "operation", value = "textDetection",
                                                          carriers = Carrier.LIST,
                                                          javaType = "java.util.List<com.google.cloud.vision.v1.EntityAnnotation>"),
                                           @BodyType.When(option = "operation", value = "faceDetection",
                                                          carriers = Carrier.LIST,
                                                          javaType = "java.util.List<com.google.cloud.vision.v1.FaceAnnotation>"),
                                           @BodyType.When(option = "operation", value = "landmarkDetection",
                                                          carriers = Carrier.LIST,
                                                          javaType = "java.util.List<com.google.cloud.vision.v1.EntityAnnotation>"),
                                           @BodyType.When(option = "operation", value = "logoDetection",
                                                          carriers = Carrier.LIST,
                                                          javaType = "java.util.List<com.google.cloud.vision.v1.EntityAnnotation>"),
                                           @BodyType.When(option = "operation", value = "objectLocalization",
                                                          carriers = Carrier.LIST,
                                                          javaType = "java.util.List<com.google.cloud.vision.v1.LocalizedObjectAnnotation>"),
                                           @BodyType.When(option = "operation", value = "safeSearchDetection",
                                                          carriers = Carrier.POJO,
                                                          javaType = "com.google.cloud.vision.v1.SafeSearchAnnotation"),
                                           @BodyType.When(option = "operation", value = "imagePropertiesDetection",
                                                          carriers = Carrier.POJO,
                                                          javaType = "com.google.cloud.vision.v1.ImageProperties"),
                                           @BodyType.When(option = "operation", value = "webDetection", carriers = Carrier.POJO,
                                                          javaType = "com.google.cloud.vision.v1.WebDetection"),
                                           @BodyType.When(option = "operation", value = "cropHintsDetection",
                                                          carriers = Carrier.POJO,
                                                          javaType = "com.google.cloud.vision.v1.CropHintsAnnotation"),
                                           @BodyType.When(option = "operation", value = "documentTextDetection",
                                                          carriers = Carrier.POJO,
                                                          javaType = "com.google.cloud.vision.v1.TextAnnotation"),
                                           @BodyType.When(option = "pojoRequest", value = "true", carriers = Carrier.POJO,
                                                          javaType = "com.google.cloud.vision.v1.AnnotateImageResponse") },
                                   description = "The annotations of the operation: a List of them for the detections that"
                                                 + " find several things, such as labelDetection, and one annotation of the"
                                                 + " Google Cloud SDK for the others, such as the SafeSearchAnnotation of"
                                                 + " safeSearchDetection. The whole AnnotateImageResponse is in the"
                                                 + " CamelGoogleCloudVisionResponseObject header, and is the body with"
                                                 + " pojoRequest=true"))
public class GoogleCloudVisionEndpoint extends DefaultEndpoint implements EndpointServiceLocation {

    @UriParam
    private GoogleCloudVisionConfiguration configuration;

    private ImageAnnotatorClient visionClient;

    public GoogleCloudVisionEndpoint(String uri, GoogleCloudVisionComponent component,
                                     GoogleCloudVisionConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new GoogleCloudVisionProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException(
                "Cannot consume from the google-vision endpoint: " + getEndpointUri());
    }

    public GoogleCloudVisionConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Set the endpoint configuration.
     *
     * @param configuration
     */
    public void setConfiguration(GoogleCloudVisionConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (configuration.getClient() != null) {
            visionClient = configuration.getClient();
        } else {
            visionClient = GoogleCloudVisionClientFactory.create(this.getCamelContext(), configuration);
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (configuration.getClient() == null && visionClient != null) {
            visionClient.close();
        }
    }

    public ImageAnnotatorClient getClient() {
        return visionClient;
    }

    @Override
    public String getServiceUrl() {
        if (ObjectHelper.isNotEmpty(configuration.getOperation())) {
            return getServiceProtocol() + ":" + configuration.getOperation();
        }
        return null;
    }

    @Override
    public String getServiceProtocol() {
        return "vision";
    }
}
