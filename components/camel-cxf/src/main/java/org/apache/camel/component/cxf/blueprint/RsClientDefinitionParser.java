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

package org.apache.camel.component.cxf.blueprint;

import java.util.StringTokenizer;

import org.apache.aries.blueprint.ParserContext;
import org.apache.aries.blueprint.mutable.MutableBeanMetadata;
import org.apache.camel.component.cxf.CxfBlueprintEndpoint;
import org.apache.cxf.common.util.StringUtils;
import org.apache.cxf.configuration.blueprint.AbstractBPBeanDefinitionParser;
import org.osgi.service.blueprint.reflect.Metadata;
import org.w3c.dom.Element;

public class RsClientDefinitionParser extends AbstractBPBeanDefinitionParser {

    public Metadata parse(Element element, ParserContext context) {
        MutableBeanMetadata endpointConfig = context.createMetadata(MutableBeanMetadata.class);
        endpointConfig.setRuntimeClass(CxfBlueprintEndpoint.class);
        endpointConfig.addProperty("blueprintContainer", createRef(context, "blueprintContainer"));
        endpointConfig.addProperty("bundleContext", createRef(context, "blueprintBundleContext"));

        if (!StringUtils.isEmpty(getIdOrName(element))) {
            endpointConfig.setId(getIdOrName(element));
        } else {
            endpointConfig.setId("camel.cxf.endpoint." + context.generateId());
        }

        return null;
    }

    public static String getIdOrName(Element elem) {
        String id = elem.getAttribute("id");

        if (null == id || "".equals(id)) {
            String names = elem.getAttribute("name");
            if (null != names) {
                StringTokenizer st = new StringTokenizer(names, ",");
                if (st.countTokens() > 0) {
                    id = st.nextToken();
                }
            }
        }
        return id;
    }
}
