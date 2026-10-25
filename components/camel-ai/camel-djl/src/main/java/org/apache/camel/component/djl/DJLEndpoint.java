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
package org.apache.camel.component.djl;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Infer Deep Learning models from message exchanges data using Deep Java Library (DJL).
 */
@UriEndpoint(firstVersion = "3.3.0", scheme = "djl", title = "Deep Java Library", syntax = "djl:application",
             remote = false, category = { Category.AI }, producerOnly = true, headersClass = DJLConstants.class)
@BodyTypes(producerIn = @BodyType(value = {
        Carrier.BYTES, Carrier.STREAM, Carrier.FILE, Carrier.POJO, Carrier.TEXT, Carrier.LIST },
                                  byOption = {
                                          @BodyType.When(option = "application", value = "cv/image_classification",
                                                         carriers = {
                                                                 Carrier.BYTES, Carrier.STREAM, Carrier.FILE, Carrier.POJO }),
                                          @BodyType.When(option = "application", value = "cv/object_detection",
                                                         carriers = {
                                                                 Carrier.BYTES, Carrier.STREAM, Carrier.FILE, Carrier.POJO }),
                                          @BodyType.When(option = "application", value = "cv/semantic_segmentation",
                                                         carriers = {
                                                                 Carrier.BYTES, Carrier.STREAM, Carrier.FILE, Carrier.POJO }),
                                          @BodyType.When(option = "application", value = "cv/instance_segmentation",
                                                         carriers = {
                                                                 Carrier.BYTES, Carrier.STREAM, Carrier.FILE, Carrier.POJO }),
                                          @BodyType.When(option = "application", value = "cv/pose_estimation",
                                                         carriers = {
                                                                 Carrier.BYTES, Carrier.STREAM, Carrier.FILE, Carrier.POJO }),
                                          @BodyType.When(option = "application", value = "cv/action_recognition",
                                                         carriers = {
                                                                 Carrier.BYTES, Carrier.STREAM, Carrier.FILE, Carrier.POJO }),
                                          @BodyType.When(option = "application", value = "cv/word_recognition",
                                                         carriers = {
                                                                 Carrier.BYTES, Carrier.STREAM, Carrier.FILE, Carrier.POJO }),
                                          @BodyType.When(option = "application", value = "cv/image_enhancement",
                                                         carriers = {
                                                                 Carrier.BYTES, Carrier.STREAM, Carrier.FILE, Carrier.POJO }),
                                          @BodyType.When(option = "application", value = "cv/image_generation",
                                                         carriers = Carrier.LIST, javaType = "int[]"),
                                          @BodyType.When(option = "application", value = "nlp/fill_mask",
                                                         carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                          @BodyType.When(option = "application", value = "nlp/text_classification",
                                                         carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                          @BodyType.When(option = "application", value = "nlp/sentiment_analysis",
                                                         carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                          @BodyType.When(option = "application", value = "nlp/token_classification",
                                                         carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                          @BodyType.When(option = "application", value = "nlp/word_embedding",
                                                         carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                          @BodyType.When(option = "application", value = "nlp/text_generation",
                                                         carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                          @BodyType.When(option = "application", value = "nlp/machine_translation",
                                                         carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                          @BodyType.When(option = "application", value = "nlp/multiple_choice",
                                                         carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                          @BodyType.When(option = "application", value = "nlp/text_embedding",
                                                         carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                          @BodyType.When(option = "application", value = "nlp/question_answer",
                                                         carriers = { Carrier.POJO, Carrier.LIST }),
                                          @BodyType.When(option = "application", value = "audio",
                                                         carriers = {
                                                                 Carrier.BYTES, Carrier.STREAM, Carrier.FILE, Carrier.POJO }),
                                          @BodyType.When(option = "application", value = "timeseries/forecasting",
                                                         carriers = Carrier.POJO,
                                                         javaType = "ai.djl.timeseries.TimeSeriesData"),
                                          @BodyType.When(option = "application", value = "tabular/linear_regression",
                                                         carriers = Carrier.ANY),
                                          @BodyType.When(option = "application", value = "tabular/softmax_regression",
                                                         carriers = Carrier.ANY) },
                                  description = "Depends on the application. The cv applications and audio take an image"
                                                + " or audio clip as bytes, a stream, a File or a Path, or a DJL Image or"
                                                + " Audio (a BufferedImage or a file name as text is not converted);"
                                                + " cv/image_generation an int[] of seeds. The nlp applications take a String"
                                                + " only (bytes or a stream fail: convert to text first), nlp/question_answer"
                                                + " a QAInput or a String[] of the question and the paragraph,"
                                                + " timeseries/forecasting a TimeSeriesData. No body fails"),
           producerOut = @BodyType(value = { Carrier.POJO, Carrier.TEXT, Carrier.LIST, Carrier.BYTES },
                                   byOption = {
                                           @BodyType.When(option = "application", value = "cv/image_classification",
                                                          carriers = Carrier.POJO,
                                                          javaType = "ai.djl.modality.Classifications"),
                                           @BodyType.When(option = "application", value = "cv/object_detection",
                                                          carriers = Carrier.POJO,
                                                          javaType = "ai.djl.modality.cv.output.DetectedObjects"),
                                           @BodyType.When(option = "application", value = "cv/semantic_segmentation",
                                                          carriers = Carrier.POJO,
                                                          javaType = "ai.djl.modality.cv.output.CategoryMask"),
                                           @BodyType.When(option = "application", value = "cv/instance_segmentation",
                                                          carriers = Carrier.POJO,
                                                          javaType = "ai.djl.modality.cv.output.DetectedObjects"),
                                           @BodyType.When(option = "application", value = "cv/pose_estimation",
                                                          carriers = Carrier.POJO,
                                                          javaType = "ai.djl.modality.cv.output.Joints"),
                                           @BodyType.When(option = "application", value = "cv/action_recognition",
                                                          carriers = Carrier.POJO,
                                                          javaType = "ai.djl.modality.Classifications"),
                                           @BodyType.When(option = "application", value = "cv/word_recognition",
                                                          carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                           @BodyType.When(option = "application", value = "cv/image_generation",
                                                          carriers = Carrier.LIST, javaType = "ai.djl.modality.cv.Image[]"),
                                           @BodyType.When(option = "application", value = "cv/image_enhancement",
                                                          carriers = Carrier.POJO, javaType = "ai.djl.modality.cv.Image"),
                                           @BodyType.When(option = "application", value = "nlp/fill_mask",
                                                          carriers = Carrier.LIST, javaType = "java.lang.String[]"),
                                           @BodyType.When(option = "application", value = "nlp/question_answer",
                                                          carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                           @BodyType.When(option = "application", value = "nlp/text_classification",
                                                          carriers = Carrier.POJO,
                                                          javaType = "ai.djl.modality.Classifications"),
                                           @BodyType.When(option = "application", value = "nlp/sentiment_analysis",
                                                          carriers = Carrier.POJO,
                                                          javaType = "ai.djl.modality.Classifications"),
                                           @BodyType.When(option = "application", value = "nlp/token_classification",
                                                          carriers = Carrier.POJO,
                                                          javaType = "ai.djl.modality.Classifications"),
                                           @BodyType.When(option = "application", value = "nlp/word_embedding",
                                                          carriers = Carrier.BYTES, javaType = "byte[]"),
                                           @BodyType.When(option = "application", value = "nlp/text_generation",
                                                          carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                           @BodyType.When(option = "application", value = "nlp/machine_translation",
                                                          carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                           @BodyType.When(option = "application", value = "nlp/multiple_choice",
                                                          carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                           @BodyType.When(option = "application", value = "nlp/text_embedding",
                                                          carriers = Carrier.POJO, javaType = "ai.djl.ndarray.NDArray"),
                                           @BodyType.When(option = "application", value = "audio", carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "application", value = "timeseries/forecasting",
                                                          carriers = Carrier.POJO, javaType = "ai.djl.timeseries.Forecast"),
                                           @BodyType.When(option = "application", value = "tabular/linear_regression",
                                                          carriers = Carrier.UNKNOWN),
                                           @BodyType.When(option = "application", value = "tabular/softmax_regression",
                                                          carriers = Carrier.UNKNOWN) },
                                   description = "The prediction of the model, as a DJL object: Classifications,"
                                                 + " DetectedObjects, CategoryMask, Joints, an Image, an NDArray or a"
                                                 + " Forecast, or text for word_recognition, question_answer, text_generation,"
                                                 + " machine_translation, multiple_choice and audio; nlp/word_embedding gives"
                                                 + " the encoded NDList as bytes. With a custom model, it is what its"
                                                 + " translator returns. The input the model took, such as the Image, is in"
                                                 + " the CamelDjlInput header"))
public class DJLEndpoint extends DefaultEndpoint {

    @UriPath
    @Metadata(required = true, description = "Application name")
    private String application;

    @UriParam(description = "Model Artifact")
    private String artifactId;
    @UriParam(description = "Model")
    private String model;
    @UriParam(description = "Translator")
    private String translator;
    @UriParam(description = "Show progress while loading zoo models. This parameter takes effect only with zoo models",
              defaultValue = "false")
    private boolean showProgress;

    public DJLEndpoint(String uri, DJLComponent component, String application) {
        super(uri, component);
        this.application = application;
    }

    @Override
    public boolean isRemote() {
        return false;
    }

    public Producer createProducer() throws Exception {
        return new DJLProducer(this);
    }

    public Consumer createConsumer(Processor processor) {
        throw new UnsupportedOperationException("Consumer not supported");
    }

    public String getApplication() {
        return application;
    }

    public void setApplication(String application) {
        this.application = application;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getTranslator() {
        return translator;
    }

    public void setTranslator(String translator) {
        this.translator = translator;
    }

    public boolean isShowProgress() {
        return showProgress;
    }

    public void setShowProgress(boolean showProgress) {
        this.showProgress = showProgress;
    }
}
