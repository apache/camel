package org.apache.camel.component.as2.api.util;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpMessage;
import org.apache.http.util.Args;

public class HttpMessageUtils {
    
    public static String getHeaderValue(HttpMessage message, String headerName) {
        Header header = message.getFirstHeader(headerName);
        return header == null ? null : header.getValue();
    }
    
    public static void setHeaderValue(HttpMessage message, String headerName, String headerValue) {
        Args.notNull(message, "message");
        Args.notNull(headerName, "headerName");
        if (headerValue == null) {
            message.removeHeaders(headerName);
        } else {
            message.setHeader(headerName, headerValue);
        }
    }
    
    public static <T> T getEntity(HttpMessage request, Class<T> type) {
        if (request instanceof HttpEntityEnclosingRequest) {
            HttpEntity entity = ((HttpEntityEnclosingRequest)request).getEntity();
            if (entity != null && type.isInstance(entity)) {
                return type.cast(entity);
            }
        }
        return null;
    }
    
}
