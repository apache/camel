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
package org.apache.camel.component.torchserve.client.model;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.component.torchserve.client.management.model.ListModels200Response;

public class ModelList {

    private String nextPageToken = null;
    private List<Model> models = new ArrayList<>();

    public ModelList() {
    }

    public static ModelList from(ListModels200Response src) {
        ModelList modelList = new ModelList();
        modelList.setNextPageToken(src.getNextPageToken());
        modelList.setModels(src.getModels().stream().map(Model::from).toList());
        return modelList;
    }

    public String getNextPageToken() {
        return nextPageToken;
    }

    public void setNextPageToken(String nextPageToken) {
        this.nextPageToken = nextPageToken;
    }

    public List<Model> getModels() {
        return models;
    }

    public void setModels(List<Model> models) {
        this.models = models;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " {\n" +
               "    nextPageToken: " + nextPageToken + "\n" +
               "    models: " + models + "\n" +
               "}";
    }
}
