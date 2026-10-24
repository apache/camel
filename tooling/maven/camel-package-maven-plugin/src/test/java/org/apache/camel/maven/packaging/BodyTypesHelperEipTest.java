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

import java.util.List;

import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.tooling.model.BaseModel;
import org.apache.camel.tooling.model.BodyTypeModel;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.EipModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The body types of an EIP: its own sides, and the side of another model its body comes from (CAMEL-25928).
 */
class BodyTypesHelperEipTest {

    @BodyTypes(eipOut = @BodyType(from = "marshalOut", dependsOn = "variableReceive"),
               eipSteps = @BodyType(Carrier.UNCHANGED))
    static class SomeEip {
    }

    @BodyTypes(eipSteps = @BodyType(from = "steps"))
    static class StepsFromSteps {
    }

    @BodyTypes(eipOut = @BodyType(value = Carrier.TEXT, from = "expressionOut"))
    static class FromWithCarriers {
    }

    @BodyTypes(eipOut = @BodyType(from = "producerIn"))
    static class OutFromAnIn {
    }

    @BodyTypes(producerOut = @BodyType(Carrier.TEXT))
    static class ComponentSideOnEip {
    }

    @BodyTypes(eipOut = @BodyType(Carrier.TEXT))
    static class EipSideOnComponent {
    }

    @Test
    void eipSides() {
        EipModel model = eip();
        BodyTypesHelper.addBodyTypes(model, SomeEip.class.getAnnotation(BodyTypes.class), SomeEip.class);

        assertEquals(List.of(BodyTypeModel.EIP_OUT, BodyTypeModel.EIP_STEPS), List.copyOf(model.getBodyTypes().keySet()));
        BodyTypeModel out = model.getBodyType(BodyTypeModel.EIP_OUT);
        assertEquals("marshalOut", out.getFrom());
        assertTrue(out.getCarriers().isEmpty());
        assertEquals(List.of("variableReceive"), out.getDependsOn());
        assertTrue(model.getBodyType(BodyTypeModel.EIP_STEPS).isUnchanged());
        assertNull(model.getBodyType(BodyTypeModel.EIP_STEPS).getFrom());
    }

    @Test
    void stepsCannotComeFromAnotherModel() {
        assertThrows(IllegalArgumentException.class, () -> add(eip(), StepsFromSteps.class));
    }

    @Test
    void fromHasNoCarriersOfItsOwn() {
        assertThrows(IllegalArgumentException.class, () -> add(eip(), FromWithCarriers.class));
    }

    @Test
    void theBodyAfterComesFromAnOutSide() {
        assertThrows(IllegalArgumentException.class, () -> add(eip(), OutFromAnIn.class));
    }

    @Test
    void sidesOfTheirOwnKind() {
        assertThrows(IllegalArgumentException.class, () -> add(eip(), ComponentSideOnEip.class));
        assertThrows(IllegalArgumentException.class, () -> add(new ComponentModel(), EipSideOnComponent.class));
    }

    private static EipModel eip() {
        EipModel model = new EipModel();
        model.setName("someEip");
        EipModel.EipOptionModel option = new EipModel.EipOptionModel();
        option.setName("variableReceive");
        option.setType("string");
        model.addOption(option);
        return model;
    }

    private static void add(BaseModel<?> model, Class<?> owner) {
        BodyTypesHelper.addBodyTypes(model, owner.getAnnotation(BodyTypes.class), owner);
    }
}
