package org.apache.camel.component.as2.api;

import static org.apache.camel.component.as2.api.AS2Constants.APPLICATION_EDIFACT_MIME_TYPE;
import static org.apache.camel.component.as2.api.AS2Constants.AS2_FROM_HEADER;
import static org.apache.camel.component.as2.api.AS2Constants.AS2_TO_HEADER;
import static org.apache.camel.component.as2.api.AS2Constants.AS2_VERSION_HEADER;
import static org.apache.camel.component.as2.api.AS2Constants.CONNECTION_HEADER;
import static org.apache.camel.component.as2.api.AS2Constants.CONTENT_LENGTH_HEADER;
import static org.apache.camel.component.as2.api.AS2Constants.CONTENT_TYPE_HEADER;
import static org.apache.camel.component.as2.api.AS2Constants.DATE_HEADER;
import static org.apache.camel.component.as2.api.AS2Constants.EXPECT_HEADER;
import static org.apache.camel.component.as2.api.AS2Constants.MESSAGE_ID_HEADER;
import static org.apache.camel.component.as2.api.AS2Constants.SUBJECT_HEADER;
import static org.apache.camel.component.as2.api.AS2Constants.TARGET_HOST_HEADER;
import static org.apache.camel.component.as2.api.AS2Constants.USER_AGENT_HEADER;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.util.EntityUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AS2ClientSendTest {
    private static final Logger LOG = LoggerFactory.getLogger(AS2ClientSendTest.class);
    
    private static final String AS2_VERSION = "1.1";
    private static final String REQUEST_URI = "/";
    private static final String AS2_NAME = "878051556";
    private static final String SUBJECT = "Test Case";
    private static final String USER_AGENT = "AS2TestClientSend Client";
    private static final String TARGET_HOSTNAME = "localhost";
    private static final String CLIENT_FQDN = "example.org";
    private static final int TARGET_PORT = 8888;
    private static final String TARGET_HOST = TARGET_HOSTNAME + ":" + TARGET_PORT;

    public static final String EDI_MESSAGE = "UNB+UNOA:1+005435656:1+006415160:1+060515:1434+00000000000778'\n"
            +"UNH+00000000000117+INVOIC:D:97B:UN'\n"
            +"BGM+380+342459+9'\n"
            +"DTM+3:20060515:102'\n"
            +"RFF+ON:521052'\n"
            +"NAD+BY+792820524::16++CUMMINS MID-RANGE ENGINE PLANT'\n"
            +"NAD+SE+005435656::16++GENERAL WIDGET COMPANY'\n"
            +"CUX+1:USD'\n"
            +"LIN+1++157870:IN'\n"
            +"IMD+F++:::WIDGET'\n"
            +"QTY+47:1020:EA'\n"
            +"ALI+US'\n"
            +"MOA+203:1202.58'\n"
            +"PRI+INV:1.179'\n"
            +"LIN+2++157871:IN'\n"
            +"IMD+F++:::DIFFERENT WIDGET'\n"
            +"QTY+47:20:EA'\n"
            +"ALI+JP'\n"
            +"MOA+203:410'\n"
            +"PRI+INV:20.5'\n"
            +"UNS+S'\n"
            +"MOA+39:2137.58'\n"
            +"ALC+C+ABG'\n"
            +"MOA+8:525'\n"
            +"UNT+23+00000000000117'\n"
            +"UNZ+1+00000000000778'\n";
    
    public class RequestHandler implements HttpRequestHandler {

        @Override
        public void handle(HttpRequest request, HttpResponse response, HttpContext context)
                throws HttpException, IOException {

            String content = null;
            if (request instanceof HttpEntityEnclosingRequest) {
                HttpEntityEnclosingRequest entityEnclosingRequest = (HttpEntityEnclosingRequest) request;
                HttpEntity entity = entityEnclosingRequest.getEntity();
                content = EntityUtils.toString(entity);
            }
            try {
                if (!requestQueue.offer(request, 10, TimeUnit.SECONDS)) {
                    LOG.error("Request not enqueued");
                }
                if (content != null && !contentQueue.offer(content, 10, TimeUnit.SECONDS)) {
                    LOG.error("Content not enqueued");
                }
            } catch (InterruptedException e) {
                LOG.error("Interrupted waiting on request queue", e);
            }
        }
        
    }

    private AS2ServerConnection as2ServerConnection;
    
    private ArrayBlockingQueue<HttpRequest> requestQueue = new ArrayBlockingQueue<HttpRequest>(1);
    private ArrayBlockingQueue<String> contentQueue = new ArrayBlockingQueue<String>(1);
    
    @Before
    public void setup() throws IOException {
        startServer();
    }
    
    @After
    public void tearDown() {
        stopServer();
    }

    @Test
    public void testListen() throws Exception {
        sendTestMessage();
        
        // Retrieve request from server
        HttpRequest request = requestQueue.poll(10, TimeUnit.SECONDS);
        
        // Validate Request Headers
        assertNotNull("Request is null", request);
        assertEquals("Request URI: ", REQUEST_URI, request.getRequestLine().getUri());
        assertEquals(AS2_VERSION_HEADER + ": ", AS2_VERSION, request.getFirstHeader(AS2_VERSION_HEADER).getValue());
        assertEquals(CONTENT_TYPE_HEADER + ": ", APPLICATION_EDIFACT_MIME_TYPE, request.getFirstHeader(CONTENT_TYPE_HEADER).getValue());
        assertEquals(AS2_FROM_HEADER + ": ", AS2_NAME, request.getFirstHeader(AS2_FROM_HEADER).getValue());
        assertEquals(AS2_TO_HEADER + ": ", AS2_NAME, request.getFirstHeader(AS2_TO_HEADER).getValue());
        assertEquals(SUBJECT_HEADER + ": ", SUBJECT, request.getFirstHeader(SUBJECT_HEADER).getValue());
        assertThat(MESSAGE_ID_HEADER + ": ", request.getFirstHeader(MESSAGE_ID_HEADER).getValue(), containsString(CLIENT_FQDN));
        assertEquals(TARGET_HOST_HEADER + ": ", TARGET_HOST, request.getFirstHeader(TARGET_HOST_HEADER).getValue());
        assertEquals(DATE_HEADER + ": ", USER_AGENT, request.getFirstHeader(USER_AGENT_HEADER).getValue());
        assertNotNull(DATE_HEADER + ": ", request.getFirstHeader(DATE_HEADER));
        assertNotNull(CONTENT_LENGTH_HEADER + ": ", request.getFirstHeader(CONTENT_LENGTH_HEADER));
        assertEquals(CONNECTION_HEADER + ": ", HTTP.CONN_KEEP_ALIVE, request.getFirstHeader(CONNECTION_HEADER).getValue());
        assertEquals(EXPECT_HEADER + ": ", HTTP.EXPECT_CONTINUE, request.getFirstHeader(EXPECT_HEADER).getValue());
        
        // Validate Request Type
        assertThat("Unexpected request type: ", request, instanceOf(HttpEntityEnclosingRequest.class));
        
        // Retrieve content from server 
        String content = contentQueue.poll(10, TimeUnit.SECONDS);
        
        // Validated content
        assertNotNull("Content is null", content);
        assertEquals("", EDI_MESSAGE, content);
    }
    
    private void startServer() throws IOException {
        as2ServerConnection = new AS2ServerConnection("AS2TestClientSend Server", 8888);
        as2ServerConnection.listen(REQUEST_URI, new RequestHandler());
    }
    
    private void stopServer() {
        as2ServerConnection.stopListening("");
        as2ServerConnection.close();
    }
    
    private void sendTestMessage() throws UnknownHostException, IOException, InvalidAS2NameException, HttpException {
        AS2ClientConnection clientConnection = new AS2ClientConnection(AS2_VERSION, USER_AGENT, CLIENT_FQDN, TARGET_HOSTNAME, TARGET_PORT);
        AS2ClientManager clientManager = new AS2ClientManager(clientConnection);
        clientManager.sendNoEncryptNoSign(REQUEST_URI, EDI_MESSAGE, SUBJECT, AS2_NAME, AS2_NAME);
    }
    
}
