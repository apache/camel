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
package org.apache.camel;

import java.io.IOException;

import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.Language;

/**
 * Catalog level interface for the {@link CamelContext}
 */
public interface CatalogCamelContext extends CamelContext {

    /**
     * Returns the JSON schema representation of the component and endpoint parameters for the given component name.
     *
     * @return the json or <tt>null</tt> if the component is <b>not</b> built with JSON schema support
     */
    String getComponentParameterJsonSchema(String componentName) throws IOException;

    /**
     * Returns the JSON schema representation of the {@link DataFormat} parameters for the given data format name.
     *
     * @return the json or <tt>null</tt> if the data format does not exist
     */
    String getDataFormatParameterJsonSchema(String dataFormatName) throws IOException;

    /**
     * Returns the JSON schema representation of the {@link Language} parameters for the given language name.
     *
     * @return the json or <tt>null</tt> if the language does not exist
     */
    String getLanguageParameterJsonSchema(String languageName) throws IOException;

    /**
     * Returns the JSON schema representation of the {@link org.apache.camel.spi.DataTypeTransformer} parameters for the
     * given transformer name.
     *
     * @return the json or <tt>null</tt> if the transformer does not exist
     */
    String getTransformerParameterJsonSchema(String transformerName) throws IOException;

    /**
     * Returns the JSON schema representation of the {@link org.apache.camel.spi.annotations.DevConsole} parameters for
     * the given dev-console name.
     *
     * @return the json or <tt>null</tt> if the dev-console does not exist
     */
    String getDevConsoleParameterJsonSchema(String devConsoleName) throws IOException;

    /**
     * Returns the JSON schema representation of the EIP parameters for the given EIP name.
     *
     * @return the json or <tt>null</tt> if the EIP does not exist
     */
    String getEipParameterJsonSchema(String eipName) throws IOException;

    /**
     * Returns the JSON schema representation of the pojo bean parameters for the given bean name.
     *
     * @return the json or <tt>null</tt> if the pojo bean does not exist
     */
    String getPojoBeanParameterJsonSchema(String name) throws IOException;

}
