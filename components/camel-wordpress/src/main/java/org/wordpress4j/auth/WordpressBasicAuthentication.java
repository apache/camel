package org.wordpress4j.auth;

import org.apache.cxf.common.util.Base64Utility;
import org.apache.cxf.jaxrs.client.ClientConfiguration;
import org.apache.cxf.jaxrs.client.WebClient;

/**
 * Basic Authentication implementation for Wordpress authentication mechanism.
 * Should be used only on tested environments due to lack of security. Be aware
 * that credentials will be passed over each request to your server.
 * <p/>
 * On environments without non HTTPS this a high security risk.
 * <p/>
 * To this implementation work, the
 * <a href="https://github.com/WP-API/Basic-Auth">Basic Authentication
 * Plugin</a> must be installed into the Wordpress server.
 */
public class WordpressBasicAuthentication extends BaseWordpressAuthentication {

    public WordpressBasicAuthentication() {
    }

    public WordpressBasicAuthentication(String username, String password) {
        super(username, password);
    }

    /**
     * HTTP Basic Authentication configuration over CXF
     * {@link ClientConfiguration}.
     * 
     * @see <a href=
     *      "http://cxf.apache.org/docs/jax-rs-client-api.html#JAX-RSClientAPI-ClientsandAuthentication">CXF
     *      Clients and Authentication</a>
     */
    @Override
    public void configureAuthentication(Object api) {
        if (isCredentialsSet()) {
            final String authorizationHeader = String.format("Basic ", Base64Utility.encode(String.format("%s:%s", this.username, this.password).getBytes()));
            WebClient.client(api).header("Authorization", authorizationHeader);
        }
    }

}
