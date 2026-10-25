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
package org.apache.camel.maven.packaging;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The transformer JSON has the data types of the annotation, whose attributes are fromType and toType (CAMEL-25928).
 */
class GenerateDataTypeTransformerMojoTest {

    @Test
    void theDataTypesOfTheAnnotation() {
        AnnotationInstance annotation = AnnotationInstance.create(GenerateDataTypeTransformerMojo.DATA_TYPE_ANNOTATION, null,
                new AnnotationValue[] {
                        AnnotationValue.createStringValue("name", "my-json"),
                        AnnotationValue.createStringValue("fromType", "text-plain"),
                        AnnotationValue.createStringValue("toType", "application-json"),
                        AnnotationValue.createStringValue("description", "Text to JSON") });
        GenerateDataTypeTransformerMojo.DataTypeTransformerModel model
                = new GenerateDataTypeTransformerMojo.DataTypeTransformerModel();
        GenerateDataTypeTransformerMojo.readAnnotation(annotation, model);
        assertEquals("my-json", model.getName());
        assertEquals("text-plain", model.getFrom());
        assertEquals("application-json", model.getTo());
        assertEquals("Text to JSON", model.getDescription());
    }

    @Test
    void noDataTypes() {
        AnnotationInstance annotation = AnnotationInstance.create(GenerateDataTypeTransformerMojo.DATA_TYPE_ANNOTATION, null,
                new AnnotationValue[] { AnnotationValue.createStringValue("name", "text-plain") });
        GenerateDataTypeTransformerMojo.DataTypeTransformerModel model
                = new GenerateDataTypeTransformerMojo.DataTypeTransformerModel();
        GenerateDataTypeTransformerMojo.readAnnotation(annotation, model);
        assertNull(model.getFrom());
        assertNull(model.getTo());
    }
}
