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
package org.apache.camel.reifier.dataformat;

import org.apache.camel.CamelContext;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.model.dataformat.ThriftDataFormat;
import org.apache.camel.spi.DataFormat;

public class ThriftDataFormatReifier extends DataFormatReifier<ThriftDataFormat> {

    public ThriftDataFormatReifier(DataFormatDefinition definition) {
        super((ThriftDataFormat)definition);
    }

    @Override
    protected void configureDataFormat(DataFormat dataFormat, CamelContext camelContext) {
        if (definition.getInstanceClass() != null) {
            setProperty(camelContext, dataFormat, "instanceClass", definition.getInstanceClass());
        }
        if (definition.getContentTypeFormat() != null) {
            setProperty(camelContext, dataFormat, "contentTypeFormat", definition.getContentTypeFormat());
        }
        if (definition.getDefaultInstance() != null) {
            setProperty(camelContext, dataFormat, "defaultInstance", definition.getDefaultInstance());
        }
    }

}
