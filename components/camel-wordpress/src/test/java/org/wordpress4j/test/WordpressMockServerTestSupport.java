package org.wordpress4j.test;

import java.io.IOException;
import java.net.BindException;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wordpress4j.WordpressServiceProvider;

public abstract class WordpressMockServerTestSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(WordpressMockServerTestSupport.class);

    protected static HttpServer localServer;
    protected static WordpressServiceProvider serviceProvider;
    
    private static final int PORT = 9009;

    public WordpressMockServerTestSupport() {

    }

    @BeforeClass
    public static void setUpMockServer() throws IOException {
        // @formatter:off
        int i = 0;
        while(true) {
            try {
                localServer = createServer(PORT + i);
                localServer.start();
                break;
            } catch(BindException ex) {
                LOGGER.warn("Port {} already in use, trying next one", PORT + i);
                i++;
            }
        }
        serviceProvider = WordpressServiceProvider.getInstance();
        serviceProvider.init(getServerBaseUrl());
        // @formatter:on
        LOGGER.info("Local server up and running on address {} and port {}", localServer.getInetAddress(), localServer.getLocalPort());

    }

    private static HttpServer createServer(int port) {
        final Map<String, String> postsListCreateRequestHandlers = new HashMap<String, String>();
        postsListCreateRequestHandlers.put("GET", "/data/posts/list.json");
        postsListCreateRequestHandlers.put("POST", "/data/posts/create.json");
        
        final Map<String, String> postsSingleUpdateRequestHandlers = new HashMap<String, String>();
        postsSingleUpdateRequestHandlers.put("GET", "/data/posts/single.json");
        postsSingleUpdateRequestHandlers.put("POST", "/data/posts/update.json");
        postsSingleUpdateRequestHandlers.put("DELETE", "/data/posts/delete.json");
        
        final Map<String, String> usersListCreateRequestHandlers = new HashMap<>();
        usersListCreateRequestHandlers.put("GET", "/data/users/list.json");
        usersListCreateRequestHandlers.put("POST", "/data/users/create.json");
        
        final Map<String, String> usersSingleUpdateRequestHandlers = new HashMap<String, String>();
        usersSingleUpdateRequestHandlers.put("GET", "/data/users/single.json");
        usersSingleUpdateRequestHandlers.put("POST", "/data/users/update.json");
        usersSingleUpdateRequestHandlers.put("DELETE", "/data/users/delete.json");
        
        // @formatter:off
        return ServerBootstrap.bootstrap()
            .setListenerPort(port)
            .registerHandler("/wp/v2/posts", new WordpressServerHttpRequestHandler(postsListCreateRequestHandlers))
            .registerHandler("/wp/v2/posts/*", new WordpressServerHttpRequestHandler(postsSingleUpdateRequestHandlers))
            .registerHandler("/wp/v2/users", new WordpressServerHttpRequestHandler(usersListCreateRequestHandlers))
            .registerHandler("/wp/v2/users/*", new WordpressServerHttpRequestHandler(usersSingleUpdateRequestHandlers))
            .create();
        // @formatter:on
    }

    @AfterClass
    public static void tearDownMockServer() {
        LOGGER.info("Stopping local server");
        if (localServer != null) {
            localServer.stop();
        }
    }
    
    public static WordpressServiceProvider getServiceProvider() {
        return serviceProvider;
    }
    
    public static String getServerBaseUrl() {
        return "http://" + localServer.getInetAddress().getHostName() + ":" + localServer.getLocalPort();
    }
}
