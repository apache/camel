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

import ai.djl.inference.Predictor;
import ai.djl.modality.cv.Image;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.TypeConversionException;
import org.apache.camel.component.djl.DJLConstants;
import org.apache.camel.component.djl.DJLEndpoint;
import org.apache.camel.component.djl.model.AbstractPredictor;

public abstract class AbstractCvZooPredictor<T> extends AbstractPredictor {

    protected ZooModel<Image, T> model;

    public AbstractCvZooPredictor(DJLEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) {
        try {
            Image image = exchange.getIn().getBody(Image.class);
            T result = predict(exchange, image);
            exchange.getIn().setBody(result);
        } catch (TypeConversionException e) {
            throw new RuntimeCamelException(
                    "Data type is not supported. Body should be ai.djl.modality.cv.Image, byte[], InputStream or File");
        }
    }

    protected T predict(Exchange exchange, Image image) {
        exchange.getIn().setHeader(DJLConstants.INPUT, image);
        try (Predictor<Image, T> predictor = model.newPredictor()) {
            return predictor.predict(image);
        } catch (TranslateException e) {
            throw new RuntimeCamelException("Could not process input or output", e);
        }
    }
}
