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

import java.util.HashMap;
import java.util.Map;

public class Api {

    private String openapi = null;
    private Map<String, String> info = new HashMap<>();
    private Map<String, Object> paths = new HashMap<>();

    public Api() {
    }

    @SuppressWarnings("unchecked")
    public static Api from(org.apache.camel.component.torchserve.client.inference.model.ApiDescription200Response src) {
        Api api = new Api();
        api.setOpenapi(src.getOpenapi());
        api.setInfo((Map<String, String>) src.getInfo());
        api.setPaths((Map<String, Object>) src.getPaths());
        return api;
    }

    @SuppressWarnings("unchecked")
    public static Api from(org.apache.camel.component.torchserve.client.management.model.ApiDescription200Response src) {
        Api api = new Api();
        api.setOpenapi(src.getOpenapi());
        api.setInfo((Map<String, String>) src.getInfo());
        api.setPaths((Map<String, Object>) src.getPaths());
        return api;
    }

    public String getOpenapi() {
        return openapi;
    }

    public void setOpenapi(String openapi) {
        this.openapi = openapi;
    }

    public Map<String, String> getInfo() {
        return info;
    }

    public void setInfo(Map<String, String> info) {
        this.info = info;
    }

    public Map<String, Object> getPaths() {
        return paths;
    }

    public void setPaths(Map<String, Object> paths) {
        this.paths = paths;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " {\n" +
               "    openapi: " + openapi + "\n" +
               "    info: " + info + "\n" +
               "    paths: " + paths + "\n" +
               "}";
    }
}
