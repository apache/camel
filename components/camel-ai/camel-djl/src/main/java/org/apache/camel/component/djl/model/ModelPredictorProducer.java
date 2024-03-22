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
package org.apache.camel.component.djl.model;

import java.io.IOException;

import ai.djl.MalformedModelException;
import ai.djl.repository.zoo.ModelNotFoundException;
import org.apache.camel.RuntimeCamelException;

import static ai.djl.Application.CV.IMAGE_CLASSIFICATION;
import static ai.djl.Application.CV.OBJECT_DETECTION;

public final class ModelPredictorProducer {

    private ModelPredictorProducer() {
        // No-op; won't be called
    }

    public static AbstractPredictor getZooPredictor(String applicationPath, String artifactId)
            throws ModelNotFoundException, MalformedModelException, IOException {
        if (applicationPath.equals(IMAGE_CLASSIFICATION.getPath())) {
            return new ZooImageClassificationPredictor(artifactId);
        } else if (applicationPath.equals(OBJECT_DETECTION.getPath())) {
            return new ZooObjectDetectionPredictor(artifactId);
        } else {
            throw new RuntimeCamelException("Application not supported ");
        }
    }

    public static AbstractPredictor getCustomPredictor(String applicationPath, String model, String translator) {
        if (applicationPath.equals(IMAGE_CLASSIFICATION.getPath())) {
            return new CustomImageClassificationPredictor(model, translator);
        } else if (applicationPath.equals(OBJECT_DETECTION.getPath())) {
            return new CustomObjectDetectionPredictor(model, translator);
        } else {
            throw new RuntimeCamelException("Application not supported ");
        }
    }
}
