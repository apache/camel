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
package org.apache.camel.component.jacksonxml;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;

/**
 * A {@link org.apache.camel.component.jacksonxml.JacksonXMLDataFormat} that is using a list
 */
public class ListJacksonXMLDataFormat extends JacksonXMLDataFormat {

    public ListJacksonXMLDataFormat() {
        useList();
    }

    public ListJacksonXMLDataFormat(Class<?> unmarshalType) {
        super(unmarshalType);
        useList();
    }

    public ListJacksonXMLDataFormat(Class<?> unmarshalType, Class<?> jsonView) {
        super(unmarshalType, jsonView);
        useList();
    }

    public ListJacksonXMLDataFormat(Class<?> unmarshalType, Class<?> jsonView, boolean enableJaxbAnnotationModule) {
        super(unmarshalType, jsonView, enableJaxbAnnotationModule);
        useList();
    }

    public ListJacksonXMLDataFormat(XmlMapper mapper, Class<?> unmarshalType) {
        super(mapper, unmarshalType);
        useList();
    }

    public ListJacksonXMLDataFormat(XmlMapper mapper, Class<?> unmarshalType, Class<?> jsonView) {
        super(mapper, unmarshalType, jsonView);
        useList();
    }

}
