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
package org.apache.camel.model;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.model.language.NamespaceAwareExpression;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * Helper for {@link RouteContextRefDefinition}.
 */
public final class RouteContextRefDefinitionHelper {

    private static JAXBContext jaxbContext;

    private RouteContextRefDefinitionHelper() {
    }

    /**
     * Lookup the routes from the {@link RouteContextRefDefinition}.
     * <p/>
     * This implementation must be used to lookup the routes as it performs a
     * deep clone of the routes as a {@link RouteContextRefDefinition} can be
     * re-used with multiple {@link ModelCamelContext} and each context should
     * have their own instances of the routes. This is to ensure no side-effects
     * and sharing of instances between the contexts. For example such as
     * property placeholders may be context specific so the routes should not
     * use placeholders from another {@link ModelCamelContext}.
     *
     * @param camelContext the CamelContext
     * @param ref the id of the {@link RouteContextRefDefinition} to lookup and
     *            get the routes.
     * @return the routes.
     */
    @SuppressWarnings("unchecked")
    public static synchronized List<RouteDefinition> lookupRoutes(CamelContext camelContext, String ref) {
        ObjectHelper.notNull(camelContext, "camelContext");
        ObjectHelper.notNull(ref, "ref");

        List<RouteDefinition> answer = CamelContextHelper.lookup(camelContext, ref, List.class);
        if (answer == null) {
            throw new IllegalArgumentException("Cannot find RouteContext with id " + ref);
        }

        // must clone the route definitions as they can be reused with multiple
        // CamelContexts
        // and they would need their own instances of the definitions to not
        // have side effects among
        // the CamelContext - for example property placeholder resolutions etc.
        List<RouteDefinition> clones = new ArrayList<>(answer.size());
        try {
            JAXBContext jaxb = getOrCreateJAXBContext(camelContext);
            for (RouteDefinition def : answer) {
                RouteDefinition clone = cloneRouteDefinition(jaxb, def);
                if (clone != null) {
                    clones.add(clone);
                }
            }
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }

        return clones;
    }

    private static synchronized JAXBContext getOrCreateJAXBContext(final CamelContext camelContext) throws JAXBException {
        if (jaxbContext == null) {
            jaxbContext = camelContext.adapt(ExtendedCamelContext.class).getModelJAXBContextFactory().newJAXBContext();
        }
        return jaxbContext;
    }

    private static RouteDefinition cloneRouteDefinition(JAXBContext jaxbContext, RouteDefinition def) throws JAXBException {
        Marshaller marshal = jaxbContext.createMarshaller();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        marshal.marshal(def, bos);

        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        Object clone = unmarshaller.unmarshal(bis);

        if (clone instanceof RouteDefinition) {
            RouteDefinition def2 = (RouteDefinition)clone;

            // need to clone the namespaces also as they are not JAXB marshalled
            // (as they are transient)
            Iterator<ExpressionNode> it = ProcessorDefinitionHelper.filterTypeInOutputs(def.getOutputs(), ExpressionNode.class);
            Iterator<ExpressionNode> it2 = ProcessorDefinitionHelper.filterTypeInOutputs(def2.getOutputs(), ExpressionNode.class);
            while (it.hasNext() && it2.hasNext()) {
                ExpressionNode node = it.next();
                ExpressionNode node2 = it2.next();

                NamespaceAwareExpression name = null;
                NamespaceAwareExpression name2 = null;
                if (node.getExpression() instanceof NamespaceAwareExpression) {
                    name = (NamespaceAwareExpression)node.getExpression();
                }
                if (node2.getExpression() instanceof NamespaceAwareExpression) {
                    name2 = (NamespaceAwareExpression)node2.getExpression();
                }

                if (name != null && name2 != null && name.getNamespaces() != null && !name.getNamespaces().isEmpty()) {
                    Map<String, String> map = new HashMap<>();
                    map.putAll(name.getNamespaces());
                    name2.setNamespaces(map);
                }
            }

            return def2;
        }

        return null;
    }

}
