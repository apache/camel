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
package org.apache.camel.maven;

/**
 * Stores mojo related constants.
 */
public final class Constants {

    // Camel core constants.
    public static final String DEFAULT_XML_INTENTION = "  ";
    public static final int WRAP_LENGTH = 80;

    // XML constants.
    public static final String XML_SCHEMA_NAMESPACE_PREFIX = "xs";
    public static final String XML_SCHEMA_NAMESPACE_URI = "http://www.w3.org/2001/XMLSchema";
    public static final String NAME_ATTRIBUTE_NAME = "name";
    public static final String TYPE_ATTRIBUTE_NAME = "type";
    public static final String XS_ANNOTATION_ELEMENT_NAME = "xs:annotation";
    public static final String XS_DOCUMENTATION_ELEMENT_NAME = "xs:documentation";

    // Json files constants.
    public static final String PROPERTIES_ATTRIBUTE_NAME = "properties";
    public static final String JSON_SUFIX = ".json";
    public static final String DESCRIPTION_ATTRIBUTE_NAME = "description";
    public static final String DEFAULT_VALUE_ATTRIBUTE_NAME = "defaultValue";
    public static final String DEPRECATED_ATTRIBUTE_NAME = "deprecated";
    public static final String MODEL_ATTRIBUTE_NAME = "model";

    private Constants() { }
}
