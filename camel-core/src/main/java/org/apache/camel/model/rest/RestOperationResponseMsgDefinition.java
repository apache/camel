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

import org.apache.camel.spi.Metadata;

import javax.xml.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;

/**
 * To specify the rest operation parameters using Swagger.
 * <p/>
 * This maps to the Swagger Parameter Object.
 * see com.wordnik.swagger.model.Parameter
 * and https://github.com/swagger-api/swagger-spec/blob/master/versions/1.2.md#524-parameter-object.
 */
@Metadata(label = "rest")
@XmlRootElement(name = "respMsg")
@XmlAccessorType(XmlAccessType.FIELD)
public class RestOperationResponseMsgDefinition {

    @XmlTransient
    private VerbDefinition verb;

    @XmlAttribute(required = true)
    private int code;

    @XmlAttribute(required = true)
    private String message;

    @XmlAttribute
    @Metadata(defaultValue = "")
    private String responseModel;


    public RestOperationResponseMsgDefinition(VerbDefinition verb) {
        this.verb = verb;
    }

    public RestOperationResponseMsgDefinition() {
    }


    public int getCode() {
        return code != 0 ? code : 200;
    }
    /**
     * Sets the Swagger Operation's ResponseMessage code
     */
    public void setCode(int code) {
        this.code = code;
    }

    public String getResponseModel() {
        return responseModel != null ? responseModel : "";
    }

    /**
     * Sets the Swagger Operation's ResponseMessage responseModel
     */
    public void setResponseModel(String responseModel) {
        this.responseModel = responseModel;
    }

    public String getMessage() {
        return message != null ? message : "success";
    }

    /**
     * Sets the Swagger Operation's ResponseMessage message
     */
    public void setMessage(String message) {
        this.message = message;
    }


    public RestOperationResponseMsgDefinition code(int code) {
        setCode(code);
        return this;
    }

    public RestOperationResponseMsgDefinition message(String msg) {
        setMessage(msg);
        return this;
    }

    public RestOperationResponseMsgDefinition responseModel(Class<?> type) {
        setResponseModel(type.getCanonicalName());
        return this;
    }

    public RestDefinition endResponseMsg() {
        verb.getResponseMsgs().add(this);
        return verb.getRest();
    }

}
