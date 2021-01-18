package org.apache.camel.component.stitch.client.models;

public class StitchException extends RuntimeException {
    private final StitchResponse response;
    private StitchError error;

    public StitchException(StitchResponse response, Throwable cause) {
        super(cause);
        this.response = response;
    }

    public StitchException(StitchResponse response) {
        super();
        this.response = response;
    }

    public StitchException(StitchResponse response, StitchError error, Throwable cause) {
        super(cause);
        this.response = response;
        this.error = error;
    }

    public StitchException(StitchResponse response, StitchError error) {
        super();
        this.response = response;
        this.error = error;
    }

    public StitchResponse getResponse() {
        return response;
    }

    public StitchError getError() {
        return error;
    }
}
