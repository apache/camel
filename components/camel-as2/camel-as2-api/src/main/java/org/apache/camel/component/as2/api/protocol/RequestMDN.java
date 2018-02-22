package org.apache.camel.component.as2.api.protocol;

import java.io.IOException;

import org.apache.camel.component.as2.api.AS2ClientManager;
import org.apache.camel.component.as2.api.AS2Header;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.apache.http.util.CharArrayBuffer;

public class RequestMDN implements HttpRequestInterceptor {
    
    private static final String SIGNED_RECEIPT_PREFIX = "signed-receipt-protocol=optional, pkcs7-signature; signed-receipt-micalg=optional";

    @Override
    public void process(HttpRequest request, HttpContext context) throws HttpException, IOException {
        
        HttpCoreContext coreContext = HttpCoreContext.adapt(context);
        
        /* Disposition-Notification-To */
        String dispositionNotificationTo = coreContext.getAttribute(AS2ClientManager.DISPOSITION_NOTIFICATION_TO, String.class);
        if (dispositionNotificationTo != null) {
            request.addHeader(AS2Header.DISPOSITION_NOTIFICATION_TO, dispositionNotificationTo);
            
            String[] micAlgorithms = coreContext.getAttribute(AS2ClientManager.SIGNED_RECEIPT_MIC_ALGORITHMS, String[].class);
            if (micAlgorithms == null) {
                throw new HttpException("Must specify a list of preferred MIC algorithms when requesting a MDN");
            }
            
            CharArrayBuffer options = new CharArrayBuffer(SIGNED_RECEIPT_PREFIX.length() + 5 * micAlgorithms.length);
            options.append(SIGNED_RECEIPT_PREFIX);
            for(String micAlgorithm: micAlgorithms) {
                options.append("," + micAlgorithm);
            }
            
            request.addHeader(AS2Header.DISPOSITION_NOTIFICATION_OPTIONS, options.toString());
            
        }

    }

}
