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
package org.apache.camel.dataformat.bindy;

import org.apache.camel.dataformat.bindy.format.factories.FactoryRegistry;

/**
 * Factory to return {@link Format} classes for a given type.
 */
public final class FormatFactory {

    private FactoryRegistry factoryRegistry;

    public FormatFactory() {
    }

    private Format<?> doGetFormat(FormattingOptions formattingOptions) {
        return factoryRegistry.findForFormattingOptions(formattingOptions)
                .build(formattingOptions);
    }

    /**
     * Retrieves the format to use for the given type*
     */
    public Format<?> getFormat(FormattingOptions formattingOptions) throws Exception {
        if (formattingOptions.getBindyConverter() != null) {
            return formattingOptions.getBindyConverter().value().newInstance();
        }

        return doGetFormat(formattingOptions);
    }

    public void setFactoryRegistry(FactoryRegistry factoryRegistry) {
        this.factoryRegistry = factoryRegistry;
    }

    public FactoryRegistry getFactoryRegistry() {
        return factoryRegistry;
    }
}
