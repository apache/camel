package org.apache.camel.component.stitch.client.models;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * This represents the Error Object: https://www.stitchdata.com/docs/developers/import-api/api#error-object
 */
public class StitchError implements StitchModel {
    private final String error;
    private final List<Object> errors;

    public StitchError(String error, List<Object> errors) {
        this.error = error;
        this.errors = errors;
    }

    public String getError() {
        return error;
    }

    public List<Object> getErrors() {
        return errors;
    }

    @Override
    public Map<String, Object> toMap() {
        final Map<String, Object> resultAsMap = new LinkedHashMap<>();

        resultAsMap.put("error", error);
        resultAsMap.put("errors", errors);

        return resultAsMap;
    }
}
