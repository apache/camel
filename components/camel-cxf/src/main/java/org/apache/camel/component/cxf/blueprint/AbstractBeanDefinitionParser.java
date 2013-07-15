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
import org.w3c.dom.Element;

import org.apache.aries.blueprint.ParserContext;
import org.apache.aries.blueprint.mutable.MutableBeanMetadata;
import org.apache.cxf.common.util.StringUtils;
import org.apache.cxf.configuration.blueprint.AbstractBPBeanDefinitionParser;
import org.osgi.service.blueprint.reflect.BeanMetadata;



public class AbstractBeanDefinitionParser extends AbstractBPBeanDefinitionParser {
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
    
    public MutableBeanMetadata createBeanMetadata(Element element, ParserContext context, Class<?> runtimeClass) {
        MutableBeanMetadata answer = context.createMetadata(MutableBeanMetadata.class);
        answer.setRuntimeClass(runtimeClass);
        answer.addProperty("blueprintContainer", createRef(context, "blueprintContainer"));
        answer.addProperty("bundleContext", createRef(context, "blueprintBundleContext"));
        // set the Bean scope to be prototype, so we can get a new instance per looking up
        answer.setScope(BeanMetadata.SCOPE_PROTOTYPE);
        
        if (!StringUtils.isEmpty(getIdOrName(element))) {
            answer.setId(getIdOrName(element));
        } else {
            // TODO we may need to throw exception for it
            answer.setId("camel.cxf.endpoint." + runtimeClass.getSimpleName() + "." + context.generateId());
        }
        return answer;
    }

}
