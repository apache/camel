package org.apache.camel.component.stitch.client.models;

import java.util.List;

/**
 * This represents the Error Object: https://www.stitchdata.com/docs/developers/import-api/api#error-object
 */
public class StitchError {
    private final String error;
    private final List<Object> errorReasons;

    public StitchError(String error, List<Object> errorReasons) {
        this.error = error;
        this.errorReasons = errorReasons;
    }

    public String getError() {
        return error;
    }

    public List<Object> getErrorReasons() {
        return errorReasons;
    }
}
