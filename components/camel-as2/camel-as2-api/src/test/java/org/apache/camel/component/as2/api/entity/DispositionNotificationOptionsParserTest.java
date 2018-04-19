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
package org.apache.camel.component.as2.api.entity;

import org.apache.camel.component.as2.api.util.AS2HeaderUtils.Parameter;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

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
        assertNotNull("signed receipt protocol not parsed", signedReceiptProtocol);
        assertEquals("Unexpected value for signed receipt protocol attribute", SIGNED_RECEIPT_PROTOCOL_ATTRIBUTE, signedReceiptProtocol.getAttribute());
        assertEquals("Unexpected value for signed receipt protocol importance", SIGNED_RECEIPT_PROTOCOL_IMPORTANCE, signedReceiptProtocol.getImportance().getImportance());
        assertArrayEquals("Unexpected value for parameter importance", SIGNED_RECEIPT_PROTOCOL_VALUES, signedReceiptProtocol.getValues());

        Parameter signedReceiptMicalg = dispositionNotificationOptions.getSignedReceiptMicalg();
        assertNotNull("signed receipt micalg not parsed", signedReceiptProtocol);
        assertEquals("Unexpected value for signed receipt micalg attribute", SIGNED_RECEIPT_MICALG_ATTRIBUTE, signedReceiptMicalg.getAttribute());
        assertEquals("Unexpected value for signed receipt micalg importance", SIGNED_RECEIPT_MICALG_IMPORTANCE, signedReceiptMicalg.getImportance().getImportance());
        assertArrayEquals("Unexpected value for micalg importance", SIGNED_RECEIPT_MICALG_VALUES, signedReceiptMicalg.getValues());
    }

}
