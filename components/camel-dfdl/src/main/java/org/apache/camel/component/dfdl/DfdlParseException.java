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
package org.apache.camel.component.dfdl;

import java.util.List;
import java.util.StringJoiner;

import org.apache.daffodil.japi.Diagnostic;
import org.apache.daffodil.japi.ParseResult;
import org.apache.daffodil.japi.WithDiagnostics;

public class DfdlParseException extends Exception {
    private final ParseResult result;

    public DfdlParseException(ParseResult result) {
        super(diagnosticsMessage("DFDL parse failed", result));
        this.result = result;
    }

    public ParseResult getParseResult() {
        return this.result;
    }

    /**
     * Builds the exception message from the diagnostics of the parse or unparse result, so the error is not logged as
     * null.
     */
    static String diagnosticsMessage(String prefix, WithDiagnostics result) {
        List<Diagnostic> diagnostics = result != null ? result.getDiagnostics() : null;
        if (diagnostics == null || diagnostics.isEmpty()) {
            return prefix;
        }
        StringJoiner joiner = new StringJoiner("; ", prefix + ": ", "");
        for (Diagnostic d : diagnostics) {
            joiner.add(d.getMessage());
        }
        return joiner.toString();
    }
}
