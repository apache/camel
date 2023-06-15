package org.apache.camel.component.file.azure;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Helper for Camel endpoint URI strings.
 * <p>
 * In Camel, URIs are most commonly passed as strings and they are flexible e.g. they could contain expressions that
 * prior evaluation could violate URI RFC, ...
 */
final class FilesURIStrings {

    public static final char QUERY_SEPARATOR = '?';

    /**
     * Get the base uri part before the options as they can be non URI valid such as the expression using $ chars and
     * the URI constructor will regard $ as an illegal character and we don't want to enforce end users to to escape the
     * $ for the expression (file language)
     */
    static URI getBaseURI(String uri) throws URISyntaxException {
        String baseUri = uri;
        if (uri.indexOf(QUERY_SEPARATOR) != -1) {
            baseUri = uri.substring(0, uri.indexOf(QUERY_SEPARATOR));
        }
        return new URI(baseUri);
    }

}
