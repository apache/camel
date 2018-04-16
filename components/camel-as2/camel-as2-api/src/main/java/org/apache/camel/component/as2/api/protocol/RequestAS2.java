package org.apache.camel.component.as2.api.protocol;

import java.io.IOException;

import org.apache.camel.component.as2.api.AS2ClientManager;
import org.apache.camel.component.as2.api.AS2Constants;
import org.apache.camel.component.as2.api.AS2Header;
import org.apache.camel.component.as2.api.InvalidAS2NameException;
import org.apache.camel.component.as2.api.Util;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;

public class RequestAS2 implements HttpRequestInterceptor {
    
    private final String as2Version;
    private final String clientFQDN;
    
    public RequestAS2(String as2Version, String clientFQDN) {
        this.as2Version = as2Version;
        this.clientFQDN = clientFQDN;
    }

    @Override
    public void process(HttpRequest request, HttpContext context) throws HttpException, IOException {
        
        HttpCoreContext coreContext = HttpCoreContext.adapt(context);
        
        /* MIME header */
        request.addHeader(AS2Header.MIME_VERSION, AS2Constants.MIME_VERSION);
        
        /* Subject header */
        String subject = coreContext.getAttribute(AS2ClientManager.SUBJECT, String.class);
        request.addHeader(AS2Header.SUBJECT, subject);
        
        /* From header */
        String from = coreContext.getAttribute(AS2ClientManager.FROM, String.class);
        request.addHeader(AS2Header.FROM, from);

        /* AS2-Version header */
        request.addHeader(AS2Header.AS2_VERSION, as2Version);

        /* AS2-From header */
        String as2From = coreContext.getAttribute(AS2ClientManager.AS2_FROM, String.class);
        try {
            Util.validateAS2Name(as2From);
        } catch (InvalidAS2NameException e) {
            throw new HttpException("Invalid AS-From name", e);
        }
        request.addHeader(AS2Header.AS2_FROM, as2From);

        /* AS2-To header */
        String as2To = coreContext.getAttribute(AS2ClientManager.AS2_TO, String.class);
        try {
            Util.validateAS2Name(as2To);
        } catch (InvalidAS2NameException e) {
            throw new HttpException("Invalid AS-To name", e);
        }
        request.addHeader(AS2Header.AS2_TO, as2To);
        
        /* Message-Id header*/
        // SHOULD be set to aid in message reconciliation
        request.addHeader(AS2Header.MESSAGE_ID, Util.createMessageId(clientFQDN));
    }

}
