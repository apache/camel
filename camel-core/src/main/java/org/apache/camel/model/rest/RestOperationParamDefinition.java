/**
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
package org.apache.camel.model.rest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.spi.Metadata;
import org.apache.camel.util.StringHelper;

/**
 * To specify the rest operation parameters using Swagger.
 * <p/>
 * This maps to the Swagger Parameter Message Object.
 */
@Metadata(label = "rest")
@XmlRootElement(name = "param")
@XmlAccessorType(XmlAccessType.FIELD)
public class RestOperationParamDefinition {

    @XmlTransient
    private VerbDefinition verb;

    @XmlAttribute(required = true)
    private String name;

    @XmlAttribute(required = true)
    @Metadata(defaultValue = "path")
    private RestParamType type;

    @XmlAttribute
    @Metadata(defaultValue = "")
    private String description;

    @XmlAttribute
    @Metadata(defaultValue = "")
    private String defaultValue;

    @XmlAttribute
    @Metadata(defaultValue = "true")
    private Boolean required;

    @XmlAttribute
    @Metadata(defaultValue = "csv")
    private CollectionFormat collectionFormat;

    @XmlAttribute
    @Metadata(defaultValue = "string")
    private String arrayType;

    @XmlAttribute
    @Metadata(defaultValue = "string")
    private String dataType;

    @XmlAttribute
    private String dataFormat;

    @XmlElementWrapper(name = "allowableValues")
    @XmlElement(name = "value")
    private List<String> allowableValues;

    @XmlAttribute
    @Metadata(defaultValue = "")
    private String access;

    @XmlElement(name = "examples")
    private List<RestPropertyDefinition> examples;

    public RestOperationParamDefinition() {
    }

    public RestOperationParamDefinition(VerbDefinition verb) {
        this.verb = verb;
    }

    public RestParamType getType() {
        return type != null ? type : RestParamType.path;
    }

    /**
     * Sets the Swagger Parameter type.
     */
    public void setType(RestParamType type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    /**
     * Sets the Swagger Parameter name.
     */
    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description != null ? description : "";
    }

    /**
     * Sets the Swagger Parameter description.
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Sets the Swagger Parameter default value.
     */
    public String getDefaultValue() {
        return defaultValue != null ? defaultValue : "";
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public Boolean getRequired() {
        return required != null ? required : true;
    }

    /**
     * Sets the Swagger Parameter required flag.
     */
    public void setRequired(Boolean required) {
        this.required = required;
    }

    public CollectionFormat getCollectionFormat() {
        return collectionFormat;
    }

    /**
     * Sets the Swagger Parameter collection format.
     */
    public void setCollectionFormat(CollectionFormat collectionFormat) {
        this.collectionFormat = collectionFormat;
    }

    public String getArrayType() {
        return arrayType;
    }

    /**
     * Sets the Swagger Parameter array type.
     * Required if data type is "array". Describes the type of items in the array.
     */
    public void setArrayType(String arrayType) {
        this.arrayType = arrayType;
    }

    public String getDataType() {
        return dataType != null ? dataType : "string";
    }

    /**
     * Sets the Swagger Parameter data type.
     */
    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public String getDataFormat() {
        return dataFormat;
    }

    /**
     * Sets the Swagger Parameter data format.
     */
    public void setDataFormat(String dataFormat) {
        this.dataFormat = dataFormat;
    }

    public List<String> getAllowableValues() {
        if (allowableValues != null) {
            return allowableValues;
        }

        return new ArrayList<String>();
    }

    /**
     * Sets the Swagger Parameter list of allowable values (enum).
     */
    public void setAllowableValues(List<String> allowableValues) {
        this.allowableValues = allowableValues;
    }

    /**
     * Gets the Swagger Parameter paramAccess flag.
     *
     * @deprecated is not in use in swagger specification 2.0
     */
    @Deprecated
    public String getAccess() {
        return access != null ? access : "";
    }

    /**
     * Sets the Swagger Parameter paramAccess flag.
     *
     * @deprecated is not in use in swagger specification 2.0
     */
    @Deprecated
    public void setAccess(String access) {
        this.access = access;
    }

    public List<RestPropertyDefinition> getExamples() {
        return examples;
    }

    /**
     * Sets the Swagger Parameter examples.
     */
    public void setExamples(List<RestPropertyDefinition> examples) {
        this.examples = examples;
    }

    /**
     * Name of the parameter.
     * <p/>
     * This option is mandatory.
     */
    public RestOperationParamDefinition name(String name) {
        setName(name);
        return this;
    }

    /**
     * Description of the parameter.
     */
    public RestOperationParamDefinition description(String name) {
        setDescription(name);
        return this;
    }

    /**
     * The default value of the parameter.
     */
    public RestOperationParamDefinition defaultValue(String name) {
        setDefaultValue(name);
        return this;
    }

    /**
     * Whether the parameter is required
     */
    public RestOperationParamDefinition required(Boolean required) {
        setRequired(required);
        return this;
    }

    /**
     * Sets the collection format.
     */
    public RestOperationParamDefinition collectionFormat(CollectionFormat collectionFormat) {
        setCollectionFormat(collectionFormat);
        return this;
    }

    /**
     * The data type of the array data type
     */
    public RestOperationParamDefinition arrayType(String arrayType) {
        setArrayType(arrayType);
        return this;
    }

    /**
     * The data type of the parameter such as <tt>string</tt>, <tt>integer</tt>, <tt>boolean</tt>
     */
    public RestOperationParamDefinition dataType(String type) {
        setDataType(type);
        return this;
    }

    /**
     * The data format of the parameter such as <tt>binary</tt>, <tt>date</tt>, <tt>date-time</tt>, <tt>password</tt>.
     * The format is usually derived from the dataType alone. However you can set this option for more fine grained control
     * of the format in use.
     */
    public RestOperationParamDefinition dataFormat(String type) {
        setDataFormat(type);
        return this;
    }

    /**
     * Allowed values of the parameter when its an enum type
     */
    public RestOperationParamDefinition allowableValues(List<String> allowableValues) {
        setAllowableValues(allowableValues);
        return this;
    }

    /**
     * Allowed values of the parameter when its an enum type
     */
    public RestOperationParamDefinition allowableValues(String... allowableValues) {
        setAllowableValues(Arrays.asList(allowableValues));
        return this;
    }

    /**
     * The parameter type such as body, form, header, path, query
     */
    public RestOperationParamDefinition type(RestParamType type) {
        setType(type);
        return this;
    }

    /**
     * Parameter access. Use <tt>false</tt> or <tt>internal</tt> to indicate the parameter
     * should be hidden for the public.
     *
     * @deprecated is not in use in swagger specification 2.0
     */
    @Deprecated
    public RestOperationParamDefinition access(String paramAccess) {
        setAccess(paramAccess);
        return this;
    }

    /**
     * Adds a body example with the given content-type
     */
    public RestOperationParamDefinition example(String contentType, String example) {
        if (examples == null) {
            examples = new ArrayList<>();
        }
        examples.add(new RestPropertyDefinition(contentType, example));
        return this;
    }

    /**
     * Adds a single example
     */
    public RestOperationParamDefinition example(String example) {
        if (examples == null) {
            examples = new ArrayList<>();
        }
        examples.add(new RestPropertyDefinition("", example));
        return this;
    }

    /**
     * Ends the configuration of this parameter
     */
    public RestDefinition endParam() {
        // name is mandatory
        StringHelper.notEmpty(name, "name");
        verb.getParams().add(this);
        return verb.getRest();
    }

}
