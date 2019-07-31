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
package org.apache.camel.component.validator;

import org.w3c.dom.ls.LSResourceResolver;

import org.apache.camel.CamelContext;

/**
 * Can be used to create custom resource resolver for the validator endpoint.
 * This interface is useful, if the custom resource resolver depends on the
 * resource URI specified in the validator endpoint. The resource URI of the
 * endpoint can be even dynamic, like in the following example:
 * 
 * <pre>
 * {@code <camel:recipientList>} 
 * {@code      <camel:simple>validator:${header.XSD_FILE}?resourceResolverFactory=#resourceResolverFactory</camel:simple>}
 * {@code </camel:recipientList>}
 * </pre>
 * 
 * The dynamic resource URI given in ${header.XSD_FILE} will be past as
 * rootResourceUri parameter in the method
 * {@link #createResourceResolver(CamelContext, String)}
 */
public interface ValidatorResourceResolverFactory {

    /**
     * Method is called during the creation of a validator endpoint.
     * 
     * @param camelContext camel context
     * @param rootResourceUri resource URI specified in the endpoint URI
     * @return resource resolver
     */
    LSResourceResolver createResourceResolver(CamelContext camelContext, String rootResourceUri);

}
