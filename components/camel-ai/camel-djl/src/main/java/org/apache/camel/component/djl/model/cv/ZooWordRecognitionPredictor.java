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
package org.apache.camel.component.djl.model.cv;

import java.io.IOException;

import ai.djl.Application;
import ai.djl.MalformedModelException;
import ai.djl.modality.cv.Image;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ModelZoo;
import ai.djl.training.util.ProgressBar;

public class ZooWordRecognitionPredictor extends AbstractCvZooPredictor<String> {

    public ZooWordRecognitionPredictor(String artifactId) throws ModelNotFoundException, MalformedModelException,
                                                          IOException {
        Criteria<Image, String> criteria = Criteria.builder()
                .optApplication(Application.CV.WORD_RECOGNITION)
                .setTypes(Image.class, String.class)
                .optArtifactId(artifactId)
                .optProgress(new ProgressBar())
                .build();
        this.model = ModelZoo.loadModel(criteria);
    }
}
