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
package org.apache.camel.dsl.jbang.export.catalog;

/**
 * A data type of a Kamelet (spec.dataTypes): its format names the data type transformer Camel runs for it, as
 * application-octet-stream or aws2-s3:application-cloudevents.
 */
public class KameletDataTypeModel {

    /** in, out or error. */
    public String direction;
    public String name;
    /** The data type transformer: the scheme and the format of the type, as Camel joins them. */
    public String format;
    public String mediaType;
    public String description;
    /** Whether it is the default of its direction, which Camel uses unless a Pipe chooses another. */
    public boolean defaultType;

}
