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

import ai.djl.Model;
import ai.djl.inference.Predictor;
import ai.djl.translate.Translator;
import org.apache.camel.component.djl.DJLEndpoint;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * A predictor of a custom model: the model and translator beans named by the model and translator options of the
 * endpoint.
 */
public abstract class AbstractCustomPredictor<I, O> extends AbstractPredictor {

    protected final String modelName;
    protected final String translatorName;
    private volatile Model model;
    private volatile Translator<I, O> translator;

    protected AbstractCustomPredictor(DJLEndpoint endpoint) {
        super(endpoint);
        this.modelName = endpoint.getModel();
        this.translatorName = endpoint.getTranslator();
    }

    @Override
    public void start() {
        // the beans may be bound after the producer is started, so only the options are checked here
        ObjectHelper.notNull(modelName, "model");
        ObjectHelper.notNull(translatorName, "translator");
    }

    @Override
    public void close() {
        // the model belongs to the user, so it is not closed; it is looked up again when the producer starts again
        model = null;
        translator = null;
    }

    /**
     * Creates a predictor of the model and translator. They are looked up in the registry once, at the first message,
     * and a missing bean fails with a {@link org.apache.camel.NoSuchBeanException} that names it.
     */
    @SuppressWarnings("unchecked")
    protected Predictor<I, O> newPredictor() {
        Model m = model;
        if (m == null) {
            m = CamelContextHelper.mandatoryLookup(getEndpoint().getCamelContext(), modelName, Model.class);
            model = m;
        }
        Translator<I, O> t = translator;
        if (t == null) {
            t = CamelContextHelper.mandatoryLookup(getEndpoint().getCamelContext(), translatorName, Translator.class);
            translator = t;
        }
        return m.newPredictor(t);
    }
}
