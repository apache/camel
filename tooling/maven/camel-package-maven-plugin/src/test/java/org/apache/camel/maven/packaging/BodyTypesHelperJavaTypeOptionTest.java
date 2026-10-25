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

import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.tooling.model.BaseModel;
import org.apache.camel.tooling.model.BodyTypeModel;
import org.apache.camel.tooling.model.EipModel;
import org.apache.camel.tooling.model.LanguageModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The option that names the Java type of the body, such as resultType of a language (CAMEL-25928).
 */
class BodyTypesHelperJavaTypeOptionTest {

    @BodyTypes(expressionIn = @BodyType(Carrier.ANY),
               expressionOut = @BodyType(value = Carrier.UNKNOWN, javaTypeOption = "resultType"))
    static class SomeLanguage {
    }

    @BodyTypes(expressionIn = @BodyType(value = Carrier.ANY, javaTypeOption = "resultType"))
    static class OnWhatTheStepNeeds {
    }

    @BodyTypes(expressionOut = @BodyType(value = Carrier.UNKNOWN, javaTypeOption = "outputType"))
    static class NotAnOption {
    }

    @BodyTypes(expressionOut = @BodyType(value = Carrier.TEXT, dependsOn = "resultType", javaTypeOption = "resultType"))
    static class AlsoInDependsOn {
    }

    @BodyTypes(expressionOut = @BodyType(value = Carrier.TEXT, javaTypeOption = "resultType",
                                         byOption = @BodyType.When(option = "resultType", value = "String",
                                                                   carriers = Carrier.TEXT)))
    static class AlsoInByOption {
    }

    @BodyTypes(eipOut = @BodyType(from = "expressionOut", javaTypeOption = "type"))
    static class WithFrom {
    }

    @Test
    void javaTypeOption() {
        LanguageModel model = language();
        add(model, SomeLanguage.class);

        BodyTypeModel out = model.getBodyType(BodyTypeModel.EXPRESSION_OUT);
        assertEquals("resultType", out.getJavaTypeOption());
        assertTrue(out.isUnknown());
        assertNull(model.getBodyType(BodyTypeModel.EXPRESSION_IN).getJavaTypeOption());
    }

    @Test
    void onlyForTheBodyAfterAStep() {
        assertThrows(IllegalArgumentException.class, () -> add(language(), OnWhatTheStepNeeds.class));
    }

    @Test
    void anOptionOfTheModel() {
        assertThrows(IllegalArgumentException.class, () -> add(language(), NotAnOption.class));
    }

    @Test
    void oneMeaningPerOption() {
        assertThrows(IllegalArgumentException.class, () -> add(language(), AlsoInDependsOn.class));
        assertThrows(IllegalArgumentException.class, () -> add(language(), AlsoInByOption.class));
    }

    @Test
    void notWithFrom() {
        EipModel model = new EipModel();
        model.setName("someEip");
        EipModel.EipOptionModel option = new EipModel.EipOptionModel();
        option.setName("type");
        option.setType("string");
        model.addOption(option);
        assertThrows(IllegalArgumentException.class, () -> add(model, WithFrom.class));
    }

    private static LanguageModel language() {
        LanguageModel model = new LanguageModel();
        model.setName("someLanguage");
        LanguageModel.LanguageOptionModel option = new LanguageModel.LanguageOptionModel();
        option.setName("resultType");
        option.setType("string");
        model.addOption(option);
        return model;
    }

    private static void add(BaseModel<?> model, Class<?> owner) {
        BodyTypesHelper.addBodyTypes(model, owner.getAnnotation(BodyTypes.class), owner);
    }
}
