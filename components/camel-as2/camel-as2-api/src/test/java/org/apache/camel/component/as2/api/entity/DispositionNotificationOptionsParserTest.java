package org.apache.camel.component.as2.api.entity;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.apache.camel.component.as2.api.entity.DispositionNotificationOptions;
import org.apache.camel.component.as2.api.entity.DispositionNotificationOptionsParser;
import org.apache.camel.component.as2.api.util.AS2HeaderUtils.Parameter;
import org.junit.Test;

public class DispositionNotificationOptionsParserTest {

    private static String TEST_NAME_VALUES = " signed-receipt-protocol   =   optional  , pkcs7-signature  ;    signed-receipt-micalg   =    required  ,  sha1  ";
    private static String SIGNED_RECEIPT_PROTOCOL_ATTRIBUTE = "signed-receipt-protocol";
    private static String SIGNED_RECEIPT_PROTOCOL_IMPORTANCE = "optional";
    private static String[] SIGNED_RECEIPT_PROTOCOL_VALUES = { "pkcs7-signature" };
    private static String SIGNED_RECEIPT_MICALG_ATTRIBUTE = "signed-receipt-micalg";
    private static String SIGNED_RECEIPT_MICALG_IMPORTANCE = "required";
    private static String[] SIGNED_RECEIPT_MICALG_VALUES = { "sha1" };

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
