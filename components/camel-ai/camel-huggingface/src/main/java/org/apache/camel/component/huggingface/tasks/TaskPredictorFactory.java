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
package org.apache.camel.component.huggingface.tasks;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.huggingface.HuggingFaceConfiguration;
import org.apache.camel.component.huggingface.HuggingFaceEndpoint;
import org.apache.camel.util.ObjectHelper;

public class TaskPredictorFactory {
    public static TaskPredictor getPredictor(HuggingFaceEndpoint endpoint) {
        HuggingFaceConfiguration config = endpoint.getConfiguration();
        HuggingFaceTask task = config.getTask();

        if (ObjectHelper.isNotEmpty(config.getPredictorBean())) {
            // Use custom bean if specified
            TaskPredictor predictor = endpoint.getCamelContext().getRegistry().lookupByNameAndType(config.getPredictorBean(),
                    TaskPredictor.class);
            if (predictor == null) {
                throw new UnsupportedOperationException("Custom predictor bean not found: " + config.getPredictorBean());
            }
            predictor.setEndpoint(endpoint);
            return predictor;
        }

        Class<? extends TaskPredictor> predictorClass = task.getPredictorClass();
        if (predictorClass == null) {
            throw new UnsupportedOperationException("Task " + task + " not supported");
        }
        try {
            return predictorClass.getConstructor(HuggingFaceEndpoint.class).newInstance(endpoint);
        } catch (Exception e) {
            throw new RuntimeCamelException("Failed to create predictor for task " + task, e);
        }
    }
}
