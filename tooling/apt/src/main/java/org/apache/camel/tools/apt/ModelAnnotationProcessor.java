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
package org.apache.camel.tools.apt;

import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.xml.bind.annotation.XmlRootElement;

import static org.apache.camel.tools.apt.AnnotationProcessorHelper.dumpExceptionToErrorFile;
import static org.apache.camel.tools.apt.helper.Strings.canonicalClassName;

/**
 * APT compiler plugin to generate JSon Schema for all EIP models and camel-spring's <camelContext> types.
 */
@SupportedAnnotationTypes({"javax.xml.bind.annotation.*", "org.apache.camel.spi.Label"})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class ModelAnnotationProcessor extends AbstractProcessor {

    private CoreEipAnnotationProcessor coreProcessor = new CoreEipAnnotationProcessor();
    private SpringAnnotationProcessor springProcessor = new SpringAnnotationProcessor();

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        try {
            if (roundEnv.processingOver()) {
                return true;
            }

            Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(XmlRootElement.class);
            for (Element element : elements) {
                if (element instanceof TypeElement) {
                    TypeElement classElement = (TypeElement) element;

                    final String javaTypeName = canonicalClassName(classElement.getQualifiedName().toString());
                    boolean core = javaTypeName.startsWith("org.apache.camel.model");
                    boolean spring = javaTypeName.startsWith("org.apache.camel.spring") || javaTypeName.startsWith("org.apache.camel.core.xml");
                    if (core) {
                        coreProcessor.processModelClass(processingEnv, roundEnv, classElement);
                    } else if (spring) {
                        springProcessor.processModelClass(processingEnv, roundEnv, classElement);
                    }
                }
            }
        } catch (Throwable e) {
            dumpExceptionToErrorFile("camel-apt-error.log", "Error processing", e);
        }

        return true;
    }

}