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

import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class DJLProducerTest {

    // Stopping the producer must release the predictor so a zoo model does not leak native memory across
    // restart/redeploy (DJLProducer.doStop -> AbstractPredictor.close). For the custom (model-less) predictor
    // path close() is the inherited no-op, so stopping must complete without error. The zoo predictors close
    // their loaded model in their own close() overrides.
    @Test
    void stoppingProducerReleasesPredictor() {
        DJLEndpoint endpoint = new DJLEndpoint("djl:tabular/linear_regression", null, "tabular/linear_regression");
        endpoint.setCamelContext(new DefaultCamelContext());
        endpoint.setModel("MyModel");
        endpoint.setTranslator("MyTranslator");

        assertDoesNotThrow(() -> {
            DJLProducer producer = new DJLProducer(endpoint);
            producer.start();
            producer.stop();
        });
    }
}
