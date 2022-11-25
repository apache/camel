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
package org.apache.camel.language;

import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v24.message.ADT_A01;
import ca.uhn.hl7v2.model.v24.segment.PID;
import org.apache.camel.builder.LanguageBuilderFactory;
import org.apache.camel.model.language.Hl7TerserExpression;

/**
 * Ensures that the "hl7terser" language is compliant with the single input / typed language expectations.
 */
class Hl7TerserLanguageTest extends AbstractSingleInputTypedLanguageTest<Hl7TerserExpression.Builder, Hl7TerserExpression> {

    private static final String PATIENT_ID = "1";

    Hl7TerserLanguageTest() {
        super("PID-3-1", LanguageBuilderFactory::hl7terser);
    }

    @Override
    protected Object defaultContentToSend() {
        try {
            return createADT01Message();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Message createADT01Message() throws Exception {
        ADT_A01 adt = new ADT_A01();
        adt.initQuickstart("ADT", "A01", "P");

        // Populate the PID Segment
        PID pid = adt.getPID();
        pid.getPatientName(0).getFamilyName().getSurname().setValue("Smith");
        pid.getPatientName(0).getGivenName().setValue("John");
        pid.getPatientIdentifierList(0).getID().setValue(PATIENT_ID);

        return adt;
    }
}
