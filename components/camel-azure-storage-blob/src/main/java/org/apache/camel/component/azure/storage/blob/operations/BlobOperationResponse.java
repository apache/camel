package org.apache.camel.component.azure.storage.blob.operations;

import java.util.HashMap;
import java.util.Map;

public class BlobOperationResponse {

    private Object body;
    private Map<String, Object> headers = new HashMap<>();

    public BlobOperationResponse() {
    }

    public BlobOperationResponse(final Object body, final Map<String, Object> headers) {
        this.body = body;
        this.headers = headers;
    }

    public BlobOperationResponse(final Object body) {
        setBody(body);
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

    public void setHeaders(final Map<String, Object> headers) {
        this.headers = headers;
    }
}
