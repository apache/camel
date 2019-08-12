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
package org.apache.camel.tools.apt;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.xml.bind.annotation.XmlRootElement;

import static org.apache.camel.tools.apt.helper.Strings.canonicalClassName;

/**
 * APT compiler plugin to generate JSon Schema for all EIP models and camel-spring's <camelContext> types.
 */
@SupportedAnnotationTypes({"javax.xml.bind.annotation.*", "org.apache.camel.spi.Label"})
public class ModelAnnotationProcessor extends AbstractCamelAnnotationProcessor {

    private CoreEipAnnotationProcessorHelper coreProcessor = new CoreEipAnnotationProcessorHelper();
    private SpringAnnotationProcessorHelper springProcessor = new SpringAnnotationProcessorHelper();

    @Override
    protected void doProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) throws Exception {
        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(XmlRootElement.class);

        Set<? extends Element> coreElements = elements.stream()
                .filter(new Predicate<Element>() {
                    @Override
                    public boolean test(Element element) {
                        if (element instanceof TypeElement) {
                            TypeElement classElement = (TypeElement) element;

                            final String javaTypeName = canonicalClassName(classElement.getQualifiedName().toString());
                            return javaTypeName.startsWith("org.apache.camel.model");
                        }
                        return false;
                    }
                }).collect(Collectors.toSet());

        Set<? extends Element> springElements = elements.stream()
                .filter(new Predicate<Element>() {
                    @Override
                    public boolean test(Element element) {
                        if (element instanceof TypeElement) {
                            TypeElement classElement = (TypeElement) element;

                            final String javaTypeName = canonicalClassName(classElement.getQualifiedName().toString());
                            return javaTypeName.startsWith("org.apache.camel.spring") || javaTypeName.startsWith("org.apache.camel.core.xml");
                        }
                        return false;
                    }
                }).collect(Collectors.toSet());

        // we want them to be sorted
        Set<String> propertyPlaceholderDefinitions = new TreeSet<>(String::compareToIgnoreCase);

        Iterator it = coreElements.iterator();
        while (it.hasNext()) {
            TypeElement classElement = (TypeElement) it.next();
            coreProcessor.processModelClass(processingEnv, roundEnv, classElement, propertyPlaceholderDefinitions, !it.hasNext());
        }

        it = springElements.iterator();
        while (it.hasNext()) {
            TypeElement classElement = (TypeElement) it.next();
            springProcessor.processModelClass(processingEnv, roundEnv, classElement, !it.hasNext());
        }
    }

}