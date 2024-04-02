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
package org.apache.camel.processor;

import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.CamelInternalProcessorAdvice;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.support.processor.RestBindingSupport;

/**
 * A {@link CamelInternalProcessorAdvice} that binds the REST DSL incoming and outgoing messages from sources of json or
 * xml to Java Objects.
 * <p/>
 * The binding uses {@link org.apache.camel.spi.DataFormat} for the actual work to transform from xml/json to Java
 * Objects and reverse again.
 * <p/>
 * The rest producer side is implemented in {@link org.apache.camel.component.rest.RestProducerBindingProcessor}
 */
@Deprecated
public class RestBindingAdvice extends RestBindingSupport {

    public RestBindingAdvice(CamelContext camelContext, DataFormat jsonDataFormat, DataFormat xmlDataFormat,
                             DataFormat outJsonDataFormat, DataFormat outXmlDataFormat,
                             String consumes, String produces,
                             String bindingMode, boolean skipBindingOnErrorCode,
                             boolean clientRequestValidation, boolean enableCORS,
                             boolean enableNoContentResponse,
                             Map<String, String> corsHeaders, Map<String, String> queryDefaultValues,
                             boolean requiredBody, Set<String> requiredQueryParameters,
                             Set<String> requiredHeaders) throws Exception {

        super(camelContext, jsonDataFormat, xmlDataFormat, outJsonDataFormat, outXmlDataFormat,
                consumes, produces, bindingMode, skipBindingOnErrorCode, clientRequestValidation,
                enableCORS, enableNoContentResponse, corsHeaders, queryDefaultValues,
                requiredBody, requiredQueryParameters, requiredHeaders);
    }

}
