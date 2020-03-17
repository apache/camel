package org.apache.camel.component.azure.storage.blob;

import java.util.Collections;
import java.util.Map;

public class BlobExchangeResponse {

    private Object body;
    private Map<String, Object> headers;

    public BlobExchangeResponse() {
        this.body = null;
        this.headers = Collections.emptyMap();
    }

    public BlobExchangeResponse(final Object body, final Map<String, Object> headers) {
        this.body = body;
        this.headers = headers;
    }

    public Object getBody() {
        return body;
    }

    public void setBody(Object body) {
        this.body = body;
    }

    public Map<String, Object> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, Object> headers) {
        this.headers = headers;
    }
}
