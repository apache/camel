package org.apache.camel.component.as2.api;

import static org.apache.camel.component.as2.api.AS2Constants.HTTP_USER_AGENT;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.impl.DefaultBHttpClientConnection;
import org.apache.http.impl.DefaultBHttpClientConnectionFactory;
import org.apache.http.protocol.HttpCoreContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpProcessorBuilder;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.http.protocol.RequestConnControl;
import org.apache.http.protocol.RequestContent;
import org.apache.http.protocol.RequestDate;
import org.apache.http.protocol.RequestExpectContinue;
import org.apache.http.protocol.RequestTargetHost;
import org.apache.http.protocol.RequestUserAgent;

public class AS2ClientConnection {
    
    private HttpProcessor httpProcessor;
    private HttpCoreContext httpContext;
    private DefaultBHttpClientConnection httpConnection;
    
    public AS2ClientConnection(String targetHostName, int targetPortNumber) throws UnknownHostException, IOException {
        // Build Context
        httpContext = HttpCoreContext.create();
        HttpHost targetHost = new HttpHost(targetHostName, targetPortNumber);
        httpContext.setTargetHost(targetHost);
        
        // Build Processor
        httpProcessor = HttpProcessorBuilder.create()
                .add(new RequestTargetHost())
                .add(new RequestUserAgent(HTTP_USER_AGENT))
                .add(new RequestDate())
                .add(new RequestContent())
                .add(new RequestConnControl())
                .add(new RequestExpectContinue(true)).build();
        
        // Build and Configure Connection
        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setBufferSize(8 * 1024)
                .build();
        DefaultBHttpClientConnectionFactory connectionFactory = new DefaultBHttpClientConnectionFactory(connectionConfig);

        // Create Socket
        Socket socket = new Socket(targetHost.getHostName(), targetHost.getPort());

        // Create Connection
        httpConnection = connectionFactory.createConnection(socket);
    }
    
    public HttpResponse send(HttpRequest request) throws HttpException, IOException {

        // Execute Request
        HttpRequestExecutor httpexecutor = new HttpRequestExecutor();
        httpexecutor.preProcess(request, httpProcessor, httpContext);
        HttpResponse response = httpexecutor.execute(request, httpConnection, httpContext);   
        httpexecutor.postProcess(response, httpProcessor, httpContext);

        return response;
    }

}
