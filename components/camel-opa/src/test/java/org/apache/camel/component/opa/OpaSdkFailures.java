/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.opa;

import java.io.Closeable;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.styra.opa.OPAException;
import com.styra.opa.openapi.models.errors.ClientError;
import com.styra.opa.openapi.models.errors.SDKError;
import com.styra.opa.openapi.models.errors.ServerError;

import static org.mockito.Mockito.mock;

/**
 * Failures shaped the way {@code OPAClient.evaluate} (com.styra:opa) really reports them, so a test exercises the
 * classification {@code failOpen} depends on. Any failure of the HTTP call is wrapped as the cause of an
 * {@link OPAException}; an undefined decision is an {@code OPAException} with no cause. A bare
 * {@code new OPAException("connection refused")} is neither, and would be read as an undefined decision.
 */
public final class OpaSdkFailures {

    private OpaSdkFailures() {
    }

    /** The server refused the connection. */
    public static OPAException unreachable(String path) {
        return wrap(path, new ConnectException("Connection refused"));
    }

    /** The server accepted the connection and did not answer within requestTimeout. */
    public static OPAException timedOut(String path) {
        return wrap(path, new HttpTimeoutException("request timed out"));
    }

    /** OPA evaluated the policy and it produced no result for this input. */
    public static OPAException undefinedDecision(String path) {
        return new OPAException(
                String.format("executing policy at '%s' succeeded, but OPA did not reply with a result", path));
    }

    /** OPA rejected the request as malformed: the SDK maps a 400 to {@link ClientError}. */
    public static OPAException badRequest(String path) {
        return wrap(path, new ClientError("invalid_parameter", "error(s) occurred while parsing the input"));
    }

    /** OPA could not evaluate the policy against this input: the SDK maps a 500 to {@link ServerError}. */
    public static OPAException evaluationError(String path) {
        return wrap(path, new ServerError(
                "internal_error", "eval_conflict_error: complete rules must not produce multiple outputs"));
    }

    /** Any other status: the SDK maps every 4xx but 400, and every 5xx but 500, to {@link SDKError}. */
    @SuppressWarnings("unchecked")
    public static OPAException status(String path, int statusCode) {
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        return wrap(path, new SDKError(response, statusCode, "API error occurred", new byte[0]));
    }

    /** The call was interrupted: the SDK catches the InterruptedException and wraps it like any other failure. */
    public static OPAException interrupted(String path) {
        return wrap(path, new InterruptedException("sleep interrupted"));
    }

    /** The SDK refused to build the request, so nothing was sent to the server. */
    public static OPAException rejectedBeforeSending(String path) {
        return wrap(path, new IllegalArgumentException("Request body is required"));
    }

    /** The SDK could not serialize the input document: Jackson reports that as an IOException of its own. */
    public static OPAException unserializableInput(String path) {
        return wrap(path, new JsonMappingException((Closeable) null, "No serializer found for class Object"));
    }

    /** The same serialization failure one level deeper, as an UncheckedIOException carries it. */
    public static OPAException unserializableInputUnchecked(String path) {
        return wrap(path, new UncheckedIOException(
                new JsonMappingException((Closeable) null, "No serializer found for class Object")));
    }

    private static OPAException wrap(String path, Exception cause) {
        return new OPAException(
                String.format("executing policy at '%s' with failed due to exception '%s'", path, cause), cause);
    }
}
