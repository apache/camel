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

import java.util.Map;
import java.util.Set;

import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

@SupportedAnnotationTypes({"org.apache.camel.Converter"})
public class CoreTypeConverterProcessor extends AbstractTypeConverterGenerator {

    private static final String CORE_STATIC_CLASSNAME = "org.apache.camel.impl.converter.CoreStaticTypeConverterLoader";

    @Override
    protected void doProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) throws Exception {
        if (this.processingEnv.getElementUtils().getTypeElement(CORE_STATIC_CLASSNAME) != null) {
            return;
        }

        // We're in tests, do not generate anything
        if (this.processingEnv.getElementUtils().getTypeElement("org.apache.camel.converter.ObjectConverter") == null) {
            return;
        }

        super.doProcess(annotations, roundEnv);
    }

    @Override
    String convertersKey(String currentClass) {
        // we want to write all converters into the same class
        return CORE_STATIC_CLASSNAME;
    }

    @Override
    boolean acceptClass(Element element) {
        return true;
    }

    @Override
    void writeConverters(Map<String, ClassConverters> converters) throws Exception {
        // now write all the converters into the same class
        for (Map.Entry<String, ClassConverters> entry : converters.entrySet()) {
            String key = entry.getKey();
            ClassConverters value = entry.getValue();
            writeConverters(key, null, true, value);
        }
    }

}
