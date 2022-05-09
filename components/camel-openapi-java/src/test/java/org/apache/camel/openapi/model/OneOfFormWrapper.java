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
package org.apache.camel.openapi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

public class OneOfFormWrapper {

    @JsonProperty("formType")
    String formType;

    @JsonProperty("form")
    @JsonTypeInfo(
                  use = JsonTypeInfo.Id.NAME,
                  include = JsonTypeInfo.As.EXISTING_PROPERTY,
                  property = "code")
    @JsonSubTypes({
            @Type(value = XOfFormA.class, name = "Form A"),
            @Type(value = XOfFormB.class, name = "Form B")
    })
    OneOfForm form;

    public String getFormType() {
        return this.formType;
    }

    public void setFormType(String formType) {
        this.formType = formType;
    }

    public OneOfForm getForm() {
        return this.form;
    }

    public void setForm(OneOfForm form) {
        this.form = form;
    }
}
