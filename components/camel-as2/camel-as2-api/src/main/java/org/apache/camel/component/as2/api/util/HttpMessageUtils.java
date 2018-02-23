package org.apache.camel.component.as2.api.util;

import org.apache.http.Header;
import org.apache.http.HttpMessage;

public class HttpMessageUtils {
    
    public static String getHeaderValue(HttpMessage request, String headerName) {
        Header header = request.getFirstHeader(headerName);
        return header == null ? null : header.getValue();
    }

}
