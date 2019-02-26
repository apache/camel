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
package org.apache.camel.reifier.transformer;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.transformer.DataFormatTransformer;
import org.apache.camel.model.transformer.DataFormatTransformerDefinition;
import org.apache.camel.model.transformer.TransformerDefinition;
import org.apache.camel.spi.Transformer;

class DataFormatTransformeReifier extends TransformerReifier<DataFormatTransformerDefinition> {

    DataFormatTransformeReifier(TransformerDefinition definition) {
        super((DataFormatTransformerDefinition) definition);
    }

    @Override
    protected Transformer doCreateTransformer(CamelContext context) {
        return new DataFormatTransformer(context)
                .setDataFormatType(definition.getDataFormatType())
                .setDataFormatRef(definition.getRef())
                .setModel(definition.getScheme())
                .setFrom(definition.getFromType())
                .setTo(definition.getToType());
    }

}
