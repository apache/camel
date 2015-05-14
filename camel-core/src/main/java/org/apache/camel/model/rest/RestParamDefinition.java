/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.model.rest;

import org.apache.camel.model.OptionalIdentifiedDefinition;

import java.util.List;

/**
 * Created by seb on 5/14/15.
 */
public class RestParamDefinition extends OptionalIdentifiedDefinition<RestParamDefinition> {

    private RestOperationParam parameter = new RestOperationParam();
    private VerbDefinition verb;

    public RestParamDefinition(VerbDefinition verb) {
        this.verb = verb;
    }

    @Override
    public String getLabel() {
        return "param";
    }


    public RestParamDefinition name(String name) {
        parameter.setName(name);
        return this;
    }

    public RestParamDefinition description(String name) {
        parameter.setDescription(name);
        return this;
    }

    public RestParamDefinition defaultValue(String name) {
        parameter.setDefaultValue(name);
        return this;
    }

    public RestParamDefinition required(Boolean required) {
        parameter.setRequired(required);
        return this;
    }

    public RestParamDefinition allowMultiple(Boolean allowMultiple) {
        parameter.setAllowMultiple(allowMultiple);
        return this;
    }

    public RestParamDefinition dataType(String type) {
        parameter.setDataType(type);
        return this;
    }

    public RestParamDefinition allowableValues(List<String> allowableValues) {
        parameter.setAllowableValues(allowableValues);
        return this;
    }

    public RestParamDefinition type(String type) {
        parameter.setParamType(type);
        return this;
    }

    public RestParamDefinition paramAccess(String paramAccess) {
        parameter.setParamAccess(paramAccess);
        return this;
    }

    public RestDefinition endParam() {
        verb.getParams().add(parameter);
        return verb.getRest();
    }

}
