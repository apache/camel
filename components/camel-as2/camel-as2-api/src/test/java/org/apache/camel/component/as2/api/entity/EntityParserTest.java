package org.apache.camel.component.as2.api.entity;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.apache.camel.component.as2.api.io.AS2SessionInputBuffer;
import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class EntityParserTest {
    
    private static final int DEFAULT_BUFFER_SIZE = 8 * 1024;

    public static final String  DISPOSITION_NOTIFICATION_REPORT_CONTENT = 
            "\r\n" +
            "------=_Part_56_1672293592.1028122454656\r\n" +
            "Content-Type: text/plain\r\n" +
            "Content-Transfer-Encoding: 7bit\r\n" +
            "\r\n" +
            "MDN for -\r\n" +
            " Message ID: <200207310834482A70BF63@\\\"~~foo~~\\\">\r\n" +
            "  From: \"\\\"  as2Name  \\\"\"\r\n" +
            "  To: \"0123456780000\"" +
            "  Received on: 2002-07-31 at 09:34:14 (EDT)\r\n" +
            " Status: processed\r\n" +
            " Comment: This is not a guarantee that the message has\r\n" +
            "  been completely processed or &understood by the receiving\r\n" +
            "  translator\r\n" +
            "\r\n" +
            "------=_Part_56_1672293592.1028122454656\r\n" +
            "Content-Type: message/disposition-notification\r\n" +
            "Content-Transfer-Encoding: 7bit\r\n" +
            "\r\n" +
            "Reporting-UA: AS2 Server\r\n" +
            "MDN-Gateway: dns; example.com\r\n" +
            "Original-Recipient: rfc822; 0123456780000\r\n" +
            "Final-Recipient: rfc822; 0123456780000\r\n" +
            "Original-Message-ID: <200207310834482A70BF63@\\\"~~foo~~\\\">\r\n" +
            "Disposition: automatic-action/MDN-sent-automatically;\r\n" +
            "  processed/warning: you're awesome\r\n" +
            "Failure: oops-a-failure\r\n" +
            "Error: oops-an-error\r\n" +
            "Warning: oops-a-warning\r\n" +
            "Received-content-MIC: 7v7F++fQaNB1sVLFtMRp+dF+eG4=, sha1\r\n" +
            "\r\n" +
            "------=_Part_56_1672293592.1028122454656--\r\n";
    
    public static final String DISPOSITION_NOTIFICATION_REPORT_CONTENT_BOUNDARY = "----=_Part_56_1672293592.1028122454656";
    
    public static final String DISPOSITION_NOTIFICATION_REPORT_CONTENT_CHARSET_NAME = "US-ASCII";
    
    public static final String DISPOSITION_NOTIFICATION_REPORT_CONTENT_TRANSFER_ENCODING = "7bit";

    public static final String  TEXT_PLAIN_CONTENT = 
            "MDN for -\r\n" +
            " Message ID: <200207310834482A70BF63@\\\"~~foo~~\\\">\r\n" +
            "  From: \"\\\"  as2Name  \\\"\"\r\n" +
            "  To: \"0123456780000\"" +
            "  Received on: 2002-07-31 at 09:34:14 (EDT)\r\n" +
            " Status: processed\r\n" +
            " Comment: This is not a guarantee that the message has\r\n" +
            "  been completely processed or &understood by the receiving\r\n" +
            "  translator\r\n" +
            "\r\n" +
            "------=_Part_56_1672293592.1028122454656--\r\n";
    
    public static final String TEXT_PLAIN_CONTENT_BOUNDARY = "----=_Part_56_1672293592.1028122454656";
    
    public static final String TEXT_PLAIN_CONTENT_CHARSET_NAME = "US-ASCII";
    
    public static final String TEXT_PLAIN_CONTENT_TRANSFER_ENCODING = "7bit";

    public static final String EXPECTED_TEXT_PLAIN_CONTENT =
            "MDN for -\r\n" +
            " Message ID: <200207310834482A70BF63@\\\"~~foo~~\\\">\r\n" +
            "  From: \"\\\"  as2Name  \\\"\"\r\n" +
            "  To: \"0123456780000\"" +
            "  Received on: 2002-07-31 at 09:34:14 (EDT)\r\n" +
            " Status: processed\r\n" +
            " Comment: This is not a guarantee that the message has\r\n" +
            "  been completely processed or &understood by the receiving\r\n" +
            "  translator\r\n";
            
    
    public static final String  DISPOSITION_NOTIFICATION_CONTENT = 
            "Reporting-UA: AS2 Server\r\n" +
            "MDN-Gateway: dns; example.com\r\n" +
            "Original-Recipient: rfc822; 0123456780000\r\n" +
            "Final-Recipient: rfc822; 0123456780000\r\n" +
            "Original-Message-ID: <200207310834482A70BF63@\\\"~~foo~~\\\">\r\n" +
            "Disposition: automatic-action/MDN-sent-automatically;\r\n" +
            "  processed/warning: you're awesome\r\n" +
            "Failure: oops-a-failure\r\n" +
            "Error: oops-an-error\r\n" +
            "Warning: oops-a-warning\r\n" +
            "Received-content-MIC: 7v7F++fQaNB1sVLFtMRp+dF+eG4=, sha1\r\n" +
            "\r\n" +
            "------=_Part_56_1672293592.1028122454656--\r\n";
    
    public static final String DISPOSITION_NOTIFICATION_CONTENT_BOUNDARY = "----=_Part_56_1672293592.1028122454656";
    
    public static final String DISPOSITION_NOTIFICATION_CONTENT_CHARSET_NAME = "US-ASCII";
    
    public static final String DISPOSITION_NOTIFICATION_CONTENT_TRANSFER_ENCODING = "7bit";

    public static final String EXPECTED_REPORTING_UA = "AS2 Server";
    public static final String EXPECTED_MTN_NAME = "example.com";
    public static final String EXPECTED_ORIGINAL_RECIPIENT = "rfc822; 0123456780000";
    public static final String EXPECTED_FINAL_RECIPIENT = "0123456780000";
    public static final String EXPECTED_ORIGINAL_MESSAGE_ID = "<200207310834482A70BF63@\\\"~~foo~~\\\">";
    public static final DispositionMode EXPECTED_DISPOSITION_MODE = DispositionMode.AUTOMATIC_ACTION_MDN_SENT_AUTOMATICALLY;
    public static final String EXPECTED_DISPOSITION_MODIFIER = "warning: you're awesome";
    public static final AS2DispositionType EXPECTED_DISPOSITION_TYPE = AS2DispositionType.PROCESSED;
    public static final String[] EXPECTED_FAILURE = { "oops-a-failure" };
    public static final String[] EXPECTED_ERROR = { "oops-an-error" };
    public static final String[] EXPECTED_WARNING = { "oops-a-warning" };
    public static final String EXPECTED_ENCODED_MESSAGE_DIGEST = "7v7F++fQaNB1sVLFtMRp+dF+eG4=";
    public static final String EXPECTED_DIGEST_ALGORITHM_ID = "sha1";
    
  @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }
    
    @Test
    public void parseMessageDispositionNotificationReportTest() throws Exception {
        
        InputStream is = new ByteArrayInputStream(DISPOSITION_NOTIFICATION_REPORT_CONTENT.getBytes(DISPOSITION_NOTIFICATION_REPORT_CONTENT_CHARSET_NAME));
        AS2SessionInputBuffer inbuffer = new AS2SessionInputBuffer(new HttpTransportMetricsImpl(), DEFAULT_BUFFER_SIZE, DEFAULT_BUFFER_SIZE, null);
        inbuffer.bind(is);

        DispositionNotificationMultipartReportEntity dispositionNotificationMultipartReportEntity = EntityParser.parseDispositionNotificationMultipartReportEntity(inbuffer, DISPOSITION_NOTIFICATION_REPORT_CONTENT_BOUNDARY, DISPOSITION_NOTIFICATION_REPORT_CONTENT_CHARSET_NAME, DISPOSITION_NOTIFICATION_REPORT_CONTENT_TRANSFER_ENCODING);
        
        assertNotNull("Unexpected Null disposition notification multipart entity", dispositionNotificationMultipartReportEntity);
        assertEquals("Unexpected number of body parts", 2, dispositionNotificationMultipartReportEntity.getPartCount());
        
        assertTrue("Unexpected type for first body part", dispositionNotificationMultipartReportEntity.getPart(0) instanceof TextPlainEntity);
        assertTrue("Unexpected type for second body part", dispositionNotificationMultipartReportEntity.getPart(1) instanceof AS2MessageDispositionNotificationEntity);
    }

    @Test
    public void parseTextPlainTest() throws Exception {
        
        InputStream is = new ByteArrayInputStream(TEXT_PLAIN_CONTENT.getBytes(TEXT_PLAIN_CONTENT_CHARSET_NAME));
        AS2SessionInputBuffer inbuffer = new AS2SessionInputBuffer(new HttpTransportMetricsImpl(), DEFAULT_BUFFER_SIZE, DEFAULT_BUFFER_SIZE, null);
        inbuffer.bind(is);

        TextPlainEntity textPlainEntity = EntityParser.parseTextPlainEntityBody(inbuffer, TEXT_PLAIN_CONTENT_BOUNDARY, TEXT_PLAIN_CONTENT_CHARSET_NAME, TEXT_PLAIN_CONTENT_TRANSFER_ENCODING);
        
        String text = textPlainEntity.getText();
        
        assertEquals("Unexpected text", EXPECTED_TEXT_PLAIN_CONTENT, text);
    }

    @Test
    public void parseMessageDispositionNotificationTest() throws Exception {

        InputStream is = new ByteArrayInputStream(DISPOSITION_NOTIFICATION_CONTENT.getBytes(DISPOSITION_NOTIFICATION_CONTENT_CHARSET_NAME));
        AS2SessionInputBuffer inbuffer = new AS2SessionInputBuffer(new HttpTransportMetricsImpl(), DEFAULT_BUFFER_SIZE, DEFAULT_BUFFER_SIZE, null);
        inbuffer.bind(is);
        
        AS2MessageDispositionNotificationEntity messageDispositionNotificationEntity =  EntityParser.parseMessageDispositionNotificationEntityBody(inbuffer, DISPOSITION_NOTIFICATION_CONTENT_BOUNDARY, DISPOSITION_NOTIFICATION_CONTENT_CHARSET_NAME, DISPOSITION_NOTIFICATION_CONTENT_TRANSFER_ENCODING);

        assertEquals("Unexpected Reporting UA value", EXPECTED_REPORTING_UA, messageDispositionNotificationEntity.getReportingUA());
        assertEquals("Unexpected MTN Name", EXPECTED_MTN_NAME, messageDispositionNotificationEntity.getMtnName());
        assertEquals("Unexpected Original Recipient", EXPECTED_ORIGINAL_RECIPIENT, messageDispositionNotificationEntity.getExtensionFields().get("Original-Recipient"));
        assertEquals("Unexpected Final Reciptient", EXPECTED_FINAL_RECIPIENT, messageDispositionNotificationEntity.getFinalRecipient());
        assertEquals("Unexpected Original Message ID", EXPECTED_ORIGINAL_MESSAGE_ID, messageDispositionNotificationEntity.getOriginalMessageId());
        assertEquals("Unexpected Disposition Mode", EXPECTED_DISPOSITION_MODE, messageDispositionNotificationEntity.getDispositionMode());
        assertNotNull("Unexpected Null Disposition Modifier", messageDispositionNotificationEntity.getDispositionModifier());
        assertEquals("Unexpected Disposition Modifier", EXPECTED_DISPOSITION_MODIFIER, messageDispositionNotificationEntity.getDispositionModifier().getModifier());
        assertEquals("Unexpected Disposition Type", EXPECTED_DISPOSITION_TYPE, messageDispositionNotificationEntity.getDispositionType());
        assertArrayEquals("Unexpected Failure Array value", EXPECTED_FAILURE, messageDispositionNotificationEntity.getFailureFields());
        assertArrayEquals("Unexpected Error Array value", EXPECTED_ERROR, messageDispositionNotificationEntity.getErrorFields());
        assertArrayEquals("Unexpected Warning Array value", EXPECTED_WARNING, messageDispositionNotificationEntity.getWarningFields());
        assertNotNull("Unexpected Null Received Content MIC", messageDispositionNotificationEntity.getReceivedContentMic());
        assertEquals("Unexpected Encoded Message Digest", EXPECTED_ENCODED_MESSAGE_DIGEST, messageDispositionNotificationEntity.getReceivedContentMic().getEncodedMessageDigest());
        assertEquals("Unexpected Digest Algorithm ID", EXPECTED_DIGEST_ALGORITHM_ID, messageDispositionNotificationEntity.getReceivedContentMic().getDigestAlgorithmId());
    }

}
