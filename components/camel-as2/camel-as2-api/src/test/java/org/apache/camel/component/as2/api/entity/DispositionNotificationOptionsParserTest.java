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
package org.apache.camel.component.as2.api.entity;

import org.apache.camel.component.as2.api.util.AS2HeaderUtils.Parameter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class DispositionNotificationOptionsParserTest {

    private static final String TEST_NAME_VALUES = " signed-receipt-protocol   =   optional  , pkcs7-signature  ;    signed-receipt-micalg   =    required  ,  sha1  ";
    private static final String SIGNED_RECEIPT_PROTOCOL_ATTRIBUTE = "signed-receipt-protocol";
    private static final String SIGNED_RECEIPT_PROTOCOL_IMPORTANCE = "optional";
    private static final String[] SIGNED_RECEIPT_PROTOCOL_VALUES = {"pkcs7-signature"};
    private static final String SIGNED_RECEIPT_MICALG_ATTRIBUTE = "signed-receipt-micalg";
    private static final String SIGNED_RECEIPT_MICALG_IMPORTANCE = "required";
    private static final String[] SIGNED_RECEIPT_MICALG_VALUES = {"sha1"};

    @Test
    public void parseDispositionNotificationOptionsTest() {

        DispositionNotificationOptions dispositionNotificationOptions =  DispositionNotificationOptionsParser.parseDispositionNotificationOptions(TEST_NAME_VALUES, null);
        Parameter signedReceiptProtocol = dispositionNotificationOptions.getSignedReceiptProtocol();
        assertNotNull(signedReceiptProtocol, "signed receipt protocol not parsed");
        assertEquals(SIGNED_RECEIPT_PROTOCOL_ATTRIBUTE, signedReceiptProtocol.getAttribute(), "Unexpected value for signed receipt protocol attribute");
        assertEquals(SIGNED_RECEIPT_PROTOCOL_IMPORTANCE, signedReceiptProtocol.getImportance().getImportance(), "Unexpected value for signed receipt protocol importance");
        assertArrayEquals(SIGNED_RECEIPT_PROTOCOL_VALUES, signedReceiptProtocol.getValues(), "Unexpected value for parameter importance");

        Parameter signedReceiptMicalg = dispositionNotificationOptions.getSignedReceiptMicalg();
        assertNotNull(signedReceiptProtocol, "signed receipt micalg not parsed");
        assertEquals(SIGNED_RECEIPT_MICALG_ATTRIBUTE, signedReceiptMicalg.getAttribute(), "Unexpected value for signed receipt micalg attribute");
        assertEquals(SIGNED_RECEIPT_MICALG_IMPORTANCE, signedReceiptMicalg.getImportance().getImportance(), "Unexpected value for signed receipt micalg importance");
        assertArrayEquals(SIGNED_RECEIPT_MICALG_VALUES, signedReceiptMicalg.getValues(), "Unexpected value for micalg importance");
    }

}
