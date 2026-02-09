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
package org.apache.camel.test.infra.openai.mock;

import java.util.List;

/**
 * Represents a mock expectation for an embedding request. Contains the expected input text and the embedding response
 * configuration.
 */
public class EmbeddingExpectation {
    private static final String DEFAULT_MODEL = "camel-embedding";

    private final String expectedInput;
    private List<Float> embeddingVector;
    private int embeddingSize;
    private String model;

    public EmbeddingExpectation(String expectedInput) {
        this.expectedInput = expectedInput;
        this.model = DEFAULT_MODEL;
    }

    public String getExpectedInput() {
        return expectedInput;
    }

    public List<Float> getEmbeddingVector() {
        return embeddingVector;
    }

    public void setEmbeddingVector(List<Float> embeddingVector) {
        this.embeddingVector = embeddingVector;
    }

    public int getEmbeddingSize() {
        return embeddingSize;
    }

    public void setEmbeddingSize(int embeddingSize) {
        this.embeddingSize = embeddingSize;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public boolean matches(String input) {
        return expectedInput.equals(input);
    }

    public boolean hasExplicitVector() {
        return embeddingVector != null && !embeddingVector.isEmpty();
    }

    @Override
    public String toString() {
        return String.format("EmbeddingExpectation{input='%s', vectorSize=%d, hasExplicit=%b, model='%s'}",
                expectedInput,
                hasExplicitVector() ? embeddingVector.size() : embeddingSize,
                hasExplicitVector(),
                model);
    }
}
