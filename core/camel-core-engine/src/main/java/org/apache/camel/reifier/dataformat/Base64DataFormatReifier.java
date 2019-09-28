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
import org.apache.camel.model.dataformat.Base64DataFormat;
import org.apache.camel.spi.DataFormat;

public class Base64DataFormatReifier extends DataFormatReifier<Base64DataFormat> {

    public Base64DataFormatReifier(DataFormatDefinition definition) {
        super((Base64DataFormat)definition);
    }

    @Override
    protected void configureDataFormat(DataFormat dataFormat, CamelContext camelContext) {
        if (definition.getLineLength() != null) {
            setProperty(camelContext, dataFormat, "lineLength", definition.getLineLength());
        }
        if (definition.getUrlSafe() != null) {
            setProperty(camelContext, dataFormat, "urlSafe", definition.getUrlSafe());
        }
        if (definition.getLineSeparator() != null) {
            // line separator must be a byte[]
            byte[] bytes = definition.getLineSeparator().getBytes();
            setProperty(camelContext, dataFormat, "lineSeparator", bytes);
        }
    }

}
