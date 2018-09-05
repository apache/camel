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
package org.apache.camel.model.dataformat;

import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RouteContext;

/**
 * Serialization is a data format which uses the standard Java Serialization mechanism
 * to unmarshal a binary payload into Java objects or to marshal Java objects into a binary blob.
 *
 * @version 
 */
@Metadata(firstVersion = "2.12.0", label = "dataformat,transformation,core", title = "Java Object Serialization")
@XmlRootElement(name = "serialization")
public class SerializationDataFormat extends DataFormatDefinition {

    public SerializationDataFormat() {
        super("serialization");
    }

    @Override
    protected DataFormat createDataFormat(RouteContext routeContext) {
        return new org.apache.camel.impl.SerializationDataFormat();
    }
}
