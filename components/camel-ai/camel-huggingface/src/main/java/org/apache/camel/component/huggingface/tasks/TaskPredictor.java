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

import org.apache.camel.Exchange;
import org.apache.camel.component.huggingface.HuggingFaceEndpoint;

/**
 * Defines the contract for a task-specific predictor in the Hugging Face component. Implement this Interface or
 * subclass {@link AbstractTaskPredictor} when no existing Predictor suits your use case.
 * <p>
 * This interface abstracts the logic required to perform inference for a specific Hugging Face task, such as
 * text-classification or summarization. Implementations are responsible for loading the correct model, preparing the
 * input from a Camel {@link Exchange}, executing the prediction, and processing the output back into the exchange.
 * </p>
 * <p>
 * The lifecycle of a {@code TaskPredictor} is managed by the
 * {@link org.apache.camel.component.huggingface.HuggingFaceProducer}.
 * </p>
 */
public interface TaskPredictor {

    /**
     * Loads the underlying DJL model and prepares the predictor for inference. This method is called once during the
     * producer's startup phase.
     *
     * @throws Exception if the model cannot be loaded or the Python environment cannot be configured.
     */
    void loadModel() throws Exception;

    /**
     * Performs inference using the loaded model.
     *
     * @param  exchange  The Camel {@link Exchange} containing the input data in its body and headers. The result of the
     *                   inference will be set on the message body of this exchange.
     * @throws Exception if an error occurs during input preparation, prediction, or output processing.
     */
    void predict(Exchange exchange) throws Exception;

    /**
     * Closes the model and releases any associated resources. This method is called when the producer is stopped.
     *
     * @throws Exception if an error occurs while closing resources.
     */
    void close() throws Exception;

    /**
     * Sets the endpoint on the predictor.
     *
     * @param endpoint The {@link HuggingFaceEndpoint} to be used.
     */
    void setEndpoint(HuggingFaceEndpoint endpoint);
}
