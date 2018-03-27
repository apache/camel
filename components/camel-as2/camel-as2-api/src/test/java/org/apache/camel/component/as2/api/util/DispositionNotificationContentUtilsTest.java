package org.apache.camel.component.as2.api.util;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.component.as2.api.entity.AS2MessageDispositionNotificationEntity;
import org.apache.camel.component.as2.api.entity.EntityParser;
import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.impl.io.SessionInputBufferImpl;
import org.apache.http.message.BasicLineParser;
import org.apache.http.util.CharArrayBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DispositionNotificationContentUtilsTest {
    
    public static final String  DISPOSITION_NOTIFICATION_CONTENT = "Reporting-UA: AS2 Server\r\n" +
                                                                   "Original-Recipient: rfc822; 0123456780000\r\n" +
                                                                   "Final-Recipient: rfc822; 0123456780000\r\n" +
                                                                   "Original-Message-ID: <200207310834482A70BF63@\\\"~~foo~~\\\">\r\n" +
                                                                   "Received-content-MIC: 7v7F++fQaNB1sVLFtMRp+dF+eG4=, sha1\r\n" +
                                                                   "Disposition: automatic-action/MDN-sent-automatically;\r\n" +
                                                                   "  processed\r\n" +
                                                                   "\r\n";


    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void test() throws Exception {
        
        InputStream is = new ByteArrayInputStream(DISPOSITION_NOTIFICATION_CONTENT.getBytes());
        
        SessionInputBufferImpl inbuffer = new SessionInputBufferImpl(new HttpTransportMetricsImpl(), 8 * 1024);
        inbuffer.bind(is);
        
        List<CharArrayBuffer> dispositionNotificationFields = EntityParser.parseBodyPartLines(inbuffer, null, BasicLineParser.INSTANCE, new ArrayList<CharArrayBuffer>());
        AS2MessageDispositionNotificationEntity messageDispositionNotificationEntity =  DispositionNotificationContentUtils.parseDispositionNotification(dispositionNotificationFields);
    }

}
