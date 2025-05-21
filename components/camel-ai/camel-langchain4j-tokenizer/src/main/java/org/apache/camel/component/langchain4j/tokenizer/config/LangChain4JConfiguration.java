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

package org.apache.camel.component.langchain4j.tokenizer.config;

import org.apache.camel.spi.Tokenizer;

public class LangChain4JConfiguration implements Tokenizer.Configuration {
    private int maxSegmentSize;
    private int maxOverlap;
    private String type;
    private String modelName;

    public int getMaxSegmentSize() {
        return maxSegmentSize;
    }

    @Override
    public void setMaxSegmentSize(int maxSegmentSize) {
        this.maxSegmentSize = maxSegmentSize;
    }

    public int getMaxTokens() {
        return getMaxSegmentSize();
    }

    @Override
    public void setMaxTokens(int maxTokens) {
        setMaxSegmentSize(maxTokens);
    }

    public int getMaxOverlap() {
        return maxOverlap;
    }

    @Override
    public void setMaxOverlap(int maxOverlap) {
        this.maxOverlap = maxOverlap;
    }

    public String getType() {
        return type;
    }

    @Override
    public void setType(String type) {
        this.type = type;
    }

    public String getModelName() {
        return modelName;
    }

    @Override
    public void setModelName(String modelName) {
        this.modelName = modelName;
    }
}
