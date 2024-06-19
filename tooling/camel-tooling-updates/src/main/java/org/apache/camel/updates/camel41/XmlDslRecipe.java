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
package org.apache.camel.updates.camel41;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.camel.updates.AbstractCamelXmlVisitor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.Markers;
import org.openrewrite.xml.XPathMatcher;
import org.openrewrite.xml.tree.Xml;

/**
 * <p>
 * <a href="https://camel.apache.org/manual/camel-4x-upgrade-guide-4_1.html#_xml_and_yaml_dsl">Camel Migration guide</a>
 * </p>
 *
 * Before:
 *
 * <pre>
 *     &lt;bean name=&quot;myBean&quot; type=&quot;groovy&quot; beanType=&quot;com.foo.MyBean&quot;&gt;
 *         &lt;script&gt;
 *           &lt;!-- groovy code here to create the bean --&gt;
 *         &lt;/script&gt;
 *     &lt;/bean&gt;
 * </pre>
 *
 * After:
 *
 * <pre>
 *     &lt;bean name=&quot;myBean&quot; type=&quot;com.foo.MyBean&quot;
 *           scriptLanguage=&quot;groovy&quot;&gt;
 *         &lt;script&gt;
 *           &lt;!-- groovy code here to create the bean --&gt;
 *         &lt;/script&gt;
 *     &lt;/bean&gt;
 * </pre>
 */
public class XmlDslRecipe extends Recipe {

    private static final XPathMatcher XML_BEAN_MATCHER = new XPathMatcher("*/bean");

    @Override
    public String getDisplayName() {
        return "Camel XMl DSL changes";
    }

    @Override
    public String getDescription() {
        return "Apache Camel XML DSL migration from version 4.0 to 4.1.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new AbstractCamelXmlVisitor() {

            @Override
            public Xml.Tag doVisitTag(final Xml.Tag tag, final ExecutionContext ctx) {
                Xml.Tag t = super.doVisitTag(tag, ctx);

                if (XML_BEAN_MATCHER.matches(getCursor()) && t.getChild("script").isPresent()) {
                    //type ans beanType has to be present in the attributes
                    //and their values has to be gathered
                    Optional<Xml.Attribute> typeAttr
                            = t.getAttributes().stream().filter(a -> "type".equals(a.getKeyAsString())).findAny();
                    Optional<Xml.Attribute> beanTypeAttr
                            = t.getAttributes().stream().filter(a -> "beanType".equals(a.getKeyAsString())).findAny();
                    //if values are not empty, migrate tag
                    if (typeAttr.isPresent() && !typeAttr.get().getValueAsString().isEmpty() && beanTypeAttr.isPresent()
                            && !beanTypeAttr.get().getValueAsString().isEmpty()) {
                        // gather attributes
                        List<Xml.Attribute> attrs = new ArrayList<>(t.getAttributes());
                        attrs.remove(typeAttr.get());
                        attrs.remove(beanTypeAttr.get());

                        //migrate values
                        Xml.Attribute.Value tmp = typeAttr.get().getValue();
                        attrs.add(typeAttr.get().withValue(beanTypeAttr.get().getValue()));
                        attrs.add(beanTypeAttr.get()
                                .withKey(new Xml.Ident(Tree.randomId(), "", Markers.EMPTY, "scriptLanguage")).withValue(tmp));

                        t = t.withAttributes(attrs);
                    }

                }

                return t;
            }
        };
    }
}
