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

package org.apache.camel.component.djl.model.nlp;

import java.io.IOException;

import ai.djl.Application;
import ai.djl.MalformedModelException;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ModelZoo;
import ai.djl.training.util.ProgressBar;
import org.apache.camel.component.djl.DJLEndpoint;

public class ZooFillMaskPredictor extends AbstractNlpZooPredictor<String[]> {

    public ZooFillMaskPredictor(DJLEndpoint endpoint)
            throws ModelNotFoundException, MalformedModelException, IOException {
        super(endpoint);

        Criteria.Builder<String, String[]> builder = Criteria.builder()
                .optApplication(Application.NLP.FILL_MASK)
                .setTypes(String.class, String[].class)
                .optArtifactId(endpoint.getArtifactId());
        if (endpoint.isShowProgress()) {
            builder.optProgress(new ProgressBar());
        }

        Criteria<String, String[]> criteria = builder.build();
        this.model = ModelZoo.loadModel(criteria);
    }
}
