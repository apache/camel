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
package org.apache.camel.spi;

import org.apache.camel.CamelContext;
import org.jspecify.annotations.Nullable;

/**
 * SPI that configures a JAXB {@link DataFormat} pair for XML binding in the Camel REST DSL.
 * <p/>
 * When a REST-DSL route uses a binding mode that includes XML (e.g. {@link RestConfiguration.RestBindingMode#xml} or
 * {@link RestConfiguration.RestBindingMode#json_xml}), the REST binding processor calls {@link #setupJaxb setupJaxb()}
 * to obtain configured {@link DataFormat} instances for the input and output types. This interface decouples the REST
 * binding layer in {@code camel-core} from the {@code camel-jaxb} module: the factory is discovered via the service key
 * {@link #FACTORY} and is only invoked when JAXB binding is actually needed, so routes that use JSON-only binding do
 * not require {@code camel-jaxb} on the classpath.
 * <p/>
 * See <a href="https://camel.apache.org/manual/rest-dsl.html">Rest DSL</a> in the Camel user manual.
 *
 * @see   RestConfiguration
 * @since 3.2
 */
public interface RestBindingJaxbDataFormatFactory {

    /**
     * Service factory key.
     */
    String FACTORY = "rest-binding-jaxb-dataformat-factory";

    /**
     * Setup XML data format
     */
    void setupJaxb(
            CamelContext camelContext, RestConfiguration config,
            @Nullable String type, @Nullable Class<?> typeClass, @Nullable String outType,
            @Nullable Class<?> outTypeClass,
            DataFormat jaxb, DataFormat outJaxb)
            throws Exception;

}
