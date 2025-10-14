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

package org.apache.camel.component.huggingface;

import ai.djl.translate.TranslateException;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.huggingface.tasks.TaskPredictor;
import org.apache.camel.component.huggingface.tasks.TaskPredictorFactory;
import org.apache.camel.support.DefaultProducer;

public class HuggingFaceProducer extends DefaultProducer {

    private TaskPredictor predictor;

    public HuggingFaceProducer(HuggingFaceEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        predictor = TaskPredictorFactory.getPredictor(getEndpoint());
        predictor.loadModel();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        try {
            predictor.predict(exchange);
        } catch (TranslateException e) {
            throw new RuntimeCamelException("Inference failed for task " + getEndpoint().getConfiguration().getTask(), e);
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (predictor != null) {
            predictor.close();
        }
        super.doStop();
    }

    @Override
    public HuggingFaceEndpoint getEndpoint() {
        return (HuggingFaceEndpoint) super.getEndpoint();
    }
}
