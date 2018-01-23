package org.wordpress4j.service;

import org.wordpress4j.auth.WordpressAuthentication;

/**
 * Common interface for Wordpress Service adapters.
 */
public interface WordpressService {
    
    /**
     * Sets the Wordpress Authentication Model
     * @param authentication
     */
    void setWordpressAuthentication(WordpressAuthentication authentication);

}
