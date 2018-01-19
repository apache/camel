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

import org.apache.camel.model.language.NamespaceAwareExpression;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.VerbDefinition;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * Helper for {@link org.apache.camel.model.RestContextRefDefinition}.
 */
public final class RestContextRefDefinitionHelper {

    private static JAXBContext jaxbContext;

    private RestContextRefDefinitionHelper() {
    }

    /**
     * Lookup the rests from the {@link org.apache.camel.model.RestContextRefDefinition}.
     * <p/>
     * This implementation must be used to lookup the rests as it performs a deep clone of the rests
     * as a {@link org.apache.camel.model.RestContextRefDefinition} can be re-used with multiple {@link org.apache.camel.model.ModelCamelContext} and each
     * context should have their own instances of the routes. This is to ensure no side-effects and sharing
     * of instances between the contexts. For example such as property placeholders may be context specific
     * so the routes should not use placeholders from another {@link org.apache.camel.model.ModelCamelContext}.
     *
     * @param camelContext the CamelContext
     * @param ref          the id of the {@link org.apache.camel.model.RestContextRefDefinition} to lookup and get the routes.
     * @return the rests.
     */
    @SuppressWarnings("unchecked")
    public static synchronized List<RestDefinition> lookupRests(ModelCamelContext camelContext, String ref) {
        ObjectHelper.notNull(camelContext, "camelContext");
        ObjectHelper.notNull(ref, "ref");

        List<RestDefinition> answer = CamelContextHelper.lookup(camelContext, ref, List.class);
        if (answer == null) {
            throw new IllegalArgumentException("Cannot find RestContext with id " + ref);
        }

        // must clone the rest definitions as they can be reused with multiple CamelContexts
        // and they would need their own instances of the definitions to not have side effects among
        // the CamelContext - for example property placeholder resolutions etc.
        List<RestDefinition> clones = new ArrayList<RestDefinition>(answer.size());
        try {
            JAXBContext jaxb = getOrCreateJAXBContext(camelContext);
            for (RestDefinition def : answer) {
                RestDefinition clone = cloneRestDefinition(jaxb, def);
                if (clone != null) {
                    clones.add(clone);
                }
            }
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }

        return clones;
    }

    private static synchronized JAXBContext getOrCreateJAXBContext(final ModelCamelContext camelContext) throws JAXBException {
        if (jaxbContext == null) {
            // must use classloader from CamelContext to have JAXB working
            jaxbContext = camelContext.getModelJAXBContextFactory().newJAXBContext();
        }
        return jaxbContext;
    }

    private static RestDefinition cloneRestDefinition(JAXBContext jaxbContext, RestDefinition def) throws JAXBException {
        Marshaller marshal = jaxbContext.createMarshaller();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        marshal.marshal(def, bos);

        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        Object clone = unmarshaller.unmarshal(bis);

        if (clone instanceof RestDefinition) {
            RestDefinition def2 = (RestDefinition) clone;

            Iterator<VerbDefinition> verbit1 = def.getVerbs().iterator();
            Iterator<VerbDefinition> verbit2 = def2.getVerbs().iterator();

            while (verbit1.hasNext() && verbit2.hasNext()) {
                VerbDefinition verb1 = verbit1.next();
                VerbDefinition verb2 = verbit2.next();

                if (verb1.getToOrRoute() instanceof RouteDefinition && verb2.getToOrRoute() instanceof RouteDefinition) {
                    RouteDefinition route1 = (RouteDefinition) verb1.getToOrRoute();
                    RouteDefinition route2 = (RouteDefinition) verb2.getToOrRoute();

                    // need to clone the namespaces also as they are not JAXB marshalled (as they are transient)
                    Iterator<ExpressionNode> it = ProcessorDefinitionHelper.filterTypeInOutputs(route1.getOutputs(), ExpressionNode.class);
                    Iterator<ExpressionNode> it2 = ProcessorDefinitionHelper.filterTypeInOutputs(route2.getOutputs(), ExpressionNode.class);
                    while (it.hasNext() && it2.hasNext()) {
                        ExpressionNode node = it.next();
                        ExpressionNode node2 = it2.next();

                        NamespaceAwareExpression name = null;
                        NamespaceAwareExpression name2 = null;
                        if (node.getExpression() instanceof NamespaceAwareExpression) {
                            name = (NamespaceAwareExpression) node.getExpression();
                        }
                        if (node2.getExpression() instanceof NamespaceAwareExpression) {
                            name2 = (NamespaceAwareExpression) node2.getExpression();
                        }

                        if (name != null && name2 != null && name.getNamespaces() != null && !name.getNamespaces().isEmpty()) {
                            Map<String, String> map = new HashMap<String, String>();
                            map.putAll(name.getNamespaces());
                            name2.setNamespaces(map);
                        }
                    }
                }
            }
            return def2;
        }

        return null;
    }

}
