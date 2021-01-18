package org.apache.camel.component.stitch.client.models;

import java.util.LinkedHashMap;
import java.util.Map;

public class StitchResponse implements StitchModel {
    private final int httpStatusCode;
    private final Map<String, Object> headers;
    private final String status;
    private final String message;

    public StitchResponse(int httpStatusCode, Map<String, Object> headers, String status, String message) {
        this.httpStatusCode = httpStatusCode;
        this.headers = headers;
        this.status = status;
        this.message = message;
    }

    public int getHttpStatusCode() {
        return httpStatusCode;
    }

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, Object> getHeaders() {
        return headers;
    }

    /**
     * Returns true if the request succeeded.
     *
     * @return
     *         <ul>
     *         <li>true - if the request succeeded</li>
     *         <li>false - if the request failed</li>
     *         </ul>
     */
    public boolean isOk() {
        return httpStatusCode < 300;
    }

    public String toString() {
        final String result
                = "HTTP Status Code: " + httpStatusCode + ", Response Status: " + status + ", Response Message: " + message;

        return result;
    }

    @Override
    public Map<String, Object> toMap() {
        final Map<String, Object> resultAsMap = new LinkedHashMap<>();

        resultAsMap.put("code", httpStatusCode);
        resultAsMap.put("headers", headers);
        resultAsMap.put("status", status);
        resultAsMap.put("message", message);

        return resultAsMap;
    }
}
