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
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.spi.Metadata;
import org.apache.camel.util.StringHelper;

/**
 * To specify the rest operation response messages using Swagger.
 * <p/>
 * This maps to the Swagger Response Message Object.
 */
@Metadata(label = "rest")
@XmlRootElement(name = "responseMessage")
@XmlAccessorType(XmlAccessType.FIELD)
public class RestOperationResponseMsgDefinition {

    @XmlTransient
    private VerbDefinition verb;

    @XmlAttribute
    @Metadata(defaultValue = "200")
    private String code;

    @XmlAttribute(required = true)
    private String message;

    @XmlAttribute
    @Metadata(defaultValue = "")
    private String responseModel;

    @XmlElement(name = "header")
    private List<RestOperationResponseHeaderDefinition> headers;

    @XmlElement(name = "examples")
    private List<RestPropertyDefinition> examples;

    public RestOperationResponseMsgDefinition(VerbDefinition verb) {
        this();
        this.verb = verb;
    }

    public RestOperationResponseMsgDefinition() {
        this.code = "200";
        this.message = "success";
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getResponseModel() {
        return responseModel != null ? responseModel : "";
    }

    public void setResponseModel(String responseModel) {
        this.responseModel = responseModel;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<RestOperationResponseHeaderDefinition> getHeaders() {
        return headers;
    }

    public void setHeaders(List<RestOperationResponseHeaderDefinition> headers) {
        this.headers = headers;
    }

    public List<RestPropertyDefinition> getExamples() {
        return examples;
    }

    /**
     * Examples of response messages
     */
    public void setExamples(List<RestPropertyDefinition> examples) {
        this.examples = examples;
    }

    /**
     * The response code such as a HTTP status code
     */
    public RestOperationResponseMsgDefinition code(int code) {
        setCode("" + code);
        return this;
    }

    /**
     * The response code such as a HTTP status code. Can use <tt>general</tt>, or other words
     * to indicate general error responses that do not map to a specific HTTP status code
     */
    public RestOperationResponseMsgDefinition code(String code) {
        setCode(code);
        return this;
    }

    /**
     * The response message (description)
     */
    public RestOperationResponseMsgDefinition message(String msg) {
        setMessage(msg);
        return this;
    }

    /**
     * The response model
     */
    public RestOperationResponseMsgDefinition responseModel(Class<?> type) {
        setResponseModel(type.getCanonicalName());
        return this;
    }

    /**
     * Adds an example
     */
    public RestOperationResponseMsgDefinition example(String key, String example) {
        if (examples == null) {
            examples = new ArrayList<>();
        }
        examples.add(new RestPropertyDefinition(key, example));
        return this;
    }

    /**
     * Adds a response header
     */
    public RestOperationResponseHeaderDefinition header(String name) {
        if (headers == null) {
            headers = new ArrayList<>();
        }
        RestOperationResponseHeaderDefinition header = new RestOperationResponseHeaderDefinition(this);
        header.setName(name);
        headers.add(header);
        return header;
    }

    /**
     * Ends the configuration of this response message
     */
    public RestDefinition endResponseMessage() {
        // code and message is mandatory
        StringHelper.notEmpty(code, "code");
        StringHelper.notEmpty(message, "message");
        verb.getResponseMsgs().add(this);
        return verb.getRest();
    }

}
