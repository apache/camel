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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementRef;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.DefaultPackageScanClassResolver;
import org.apache.camel.spi.BeanIntrospection;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performs sanity check on the model classes that their JAXB annotations and getter/setter match up.
 */
public class ModelSanityCheckerTest {

    private static final Logger LOG = LoggerFactory.getLogger(ModelSanityCheckerTest.class);

    private Set<Class<?>> discoverJaxbClasses() throws Exception {
        DefaultPackageScanClassResolver resolver = new DefaultPackageScanClassResolver();
        resolver.start();
        String[] packages = Constants.JAXB_CONTEXT_PACKAGES.split(":");
        return resolver.findAnnotated(XmlAccessorType.class, packages);
    }

    @Test
    public void testSanity() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.start();
        BeanIntrospection bi = PluginHelper.getBeanIntrospection(context);

        Set<Class<?>> classes = discoverJaxbClasses();
        assertNotNull(classes);
        assertTrue(classes.size() > 140, "There should be > 140 classes, was: " + classes.size());

        // check each class is okay
        for (Class<?> clazz : classes) {

            // skip ProcessorDefinition as its special
            if (clazz == ProcessorDefinition.class) {
                continue;
            }
            // skip RouteDefinition as its special
            if (clazz == RouteDefinition.class) {
                continue;
            }

            // check each declared field in the class
            for (Field field : clazz.getDeclaredFields()) {
                LOG.debug("Class {} has field {}", clazz.getName(), field.getName());

                // does the field have a jaxb annotation?
                boolean attribute = field.getAnnotation(XmlAttribute.class) != null;
                boolean element = field.getAnnotation(XmlElement.class) != null;
                boolean elementRef = field.getAnnotation(XmlElementRef.class) != null;

                // only one of those 3 is allowed, so check that we don't have
                // 2+ of them
                if (attribute && element || attribute && elementRef || element && elementRef) {
                    fail("Class " + clazz.getName() + " has field " + field.getName()
                         + " which has 2+ annotations that are not allowed together.");
                }

                // check getter/setter
                if (attribute || element || elementRef) {
                    // check for getter/setter
                    Method getter = bi.getPropertyGetter(clazz, field.getName(), false);
                    Method setter = bi.getPropertySetter(clazz, field.getName());

                    assertNotNull(getter, "Getter " + field.getName() + " on class " + clazz.getName() + " is missing");
                    assertNotNull(setter, "Setter " + field.getName() + " on class " + clazz.getName() + " is missing");
                }
            }

            // we do not expect any JAXB annotations on methods
            for (Method method : clazz.getDeclaredMethods()) {
                LOG.debug("Class {} has method {}", clazz.getName(), method.getName());

                // special for OptionalIdentifiedDefinition as it has setter, so
                // we should skip it
                if (clazz.getCanonicalName().equals(OptionalIdentifiedDefinition.class.getCanonicalName())) {
                    continue;
                }

                // does the method have a jaxb annotation?
                boolean attribute = method.getAnnotation(XmlAttribute.class) != null;
                boolean element = method.getAnnotation(XmlElement.class) != null;
                boolean elementRef = method.getAnnotation(XmlElementRef.class) != null;

                assertFalse(attribute, "Class " + clazz.getName() + " has method " + method.getName()
                                       + " should not have @XmlAttribute annotation");
                assertFalse(element, "Class " + clazz.getName() + " has method " + method.getName()
                                     + " should not have @XmlElement annotation");
                boolean b = elementRef && !"setOutputs".equals(method.getName());
                assertFalse(b, "Class " + clazz.getName() + " has method " + method.getName()
                               + " should not have @XmlElementRef annotation");
            }
        }
        context.stop();
    }

}
