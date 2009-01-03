package org.apache.camel.component.http;

import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;

public class BasicAuthenticationHttpClientConfigurer implements HttpClientConfigurer {
    private final String username;
    private final String password;
    
    public BasicAuthenticationHttpClientConfigurer(String user, String pwd) {
        username = user;
        password = pwd;
    }

    public void configureHttpClient(HttpClient client) {
        
        Credentials defaultcreds = new UsernamePasswordCredentials(username, password);
        client.getState().setCredentials(AuthScope.ANY, defaultcreds);

    }

}
