package org.apache.camel.component.stitch.client.models;

public class StitchResponse {
    private final int httpStatusCode;
    private final String status;
    private final String message;

    public StitchResponse(int httpStatusCode, String status, String message) {
        this.httpStatusCode = httpStatusCode;
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

    /**
     * Returns true if the request succeeded.
     *
     * @return <ul>
     *           <li>true - if the request succeeded</li>
     *           <li>false - if the request failed</li>
     *         </ul>
     */
    public boolean isOk() {
        return httpStatusCode < 300;
    }

    public String toString() {
        final String result = "HTTP Status Code: " + httpStatusCode + ", Response Status: " + status + ", Response Message: " + message;

        return result;
    }
}
