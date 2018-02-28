package org.apache.camel.component.as2.api.util;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpMessage;

public class HttpMessageUtils {
    
    public static String getHeaderValue(HttpMessage request, String headerName) {
        Header header = request.getFirstHeader(headerName);
        return header == null ? null : header.getValue();
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
