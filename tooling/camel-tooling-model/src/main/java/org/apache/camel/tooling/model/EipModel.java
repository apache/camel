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
package org.apache.camel.tooling.model;

import java.util.ArrayList;
import java.util.List;

public class EipModel extends BaseModel<EipModel.EipOptionModel> {

    protected boolean abstractModel;  // used in models from camel-core-engine
    protected boolean input;          // used in models from camel-core-engine
    protected boolean output;         // used in models from camel-core-engine
    protected final List<EipModel.EipOptionModel> exchangeProperties = new ArrayList<>();

    public EipModel() {
    }

    @Override
    public String getKind() {
        return "model";
    }

    public boolean isAbstractModel() {
        return abstractModel;
    }

    public void setAbstractModel(boolean abstractModel) {
        this.abstractModel = abstractModel;
    }

    public boolean isInput() {
        return input;
    }

    public void setInput(boolean input) {
        this.input = input;
    }

    public boolean isOutput() {
        return output;
    }

    public void setOutput(boolean output) {
        this.output = output;
    }

    public List<EipOptionModel> getExchangeProperties() {
        return exchangeProperties;
    }

    public void addExchangeProperty(EipOptionModel property) {
        exchangeProperties.add(property);
    }

    public String getDocLink() {
        // lets store EIP docs in a sub-folder as we have many EIPs
        return "src/main/docs/eips/";
    }

    public static class EipOptionModel extends BaseOptionModel {

    }
}
