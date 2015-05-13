package org.apache.camel.model.rest;

import org.apache.camel.model.OptionalIdentifiedDefinition;

import java.util.ArrayList;
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
