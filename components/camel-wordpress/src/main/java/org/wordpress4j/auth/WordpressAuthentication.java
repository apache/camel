package org.wordpress4j.auth;

/**
 * Wordpress Authentication Mecanism needed to perform privileged actions like
 * create, update or delete a post.
 * 
 * @see <a href=
 *      "https://developer.wordpress.org/rest-api/using-the-rest-api/authentication/">Wordpress
 *      API Authentication</a>
 * @since 0.1
 */
public interface WordpressAuthentication {

    void setPassword(String pwd);

    void setUsername(String user);

    String getUsername();

    void configureAuthentication(Object client);

}
