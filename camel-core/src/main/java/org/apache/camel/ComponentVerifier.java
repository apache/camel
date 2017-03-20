/**
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
package org.apache.camel;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ComponentVerifier {
    // Todo: should be an enum ?
    String CODE_EXCEPTION = "exception";
    String CODE_INTERNAL = "internal";
    String CODE_MISSING_OPTION = "missing-option";
    String CODE_MISSING_OPTION_GROUP = "missing-option-group";
    String CODE_UNKNOWN_OPTION = "unknown-option";
    String CODE_ILLEGAL_OPTION = "illegal-option";
    String CODE_ILLEGAL_OPTION_GROUP_COMBINATION = "illegal-option-group-combination";
    String CODE_ILLEGAL_OPTION_VALUE = "illegal-option-value";
    String CODE_INCOMPLETE_OPTION_GROUP = "incomplete-option-group";
    String CODE_UNSUPPORTED = "unsupported";
    String CODE_UNSUPPORTED_SCOPE = "unsupported-scope";
    String ERROR_TYPE_ATTRIBUTE = "error.type";
    String ERROR_TYPE_EXCEPTION = "exception";
    String ERROR_TYPE_HTTP = "http";
    String HTTP_CODE = "http.code";
    String HTTP_TEXT = "http.text";
    String HTTP_REDIRECT = "http.redirect";
    String HTTP_REDIRECT_LOCATION = "http.redirect.location";
    String EXCEPTION_CLASS = "exception.class";
    String EXCEPTION_INSTANCE = "exception.instance";
    String GROUP_NAME = "group.name";
    String GROUP_OPTIONS = "group.options";

    enum Scope {
        NONE,
        PARAMETERS,
        CONNECTIVITY
    }

    /**
     * Represent an error
     */
    interface Error extends Serializable {
        String getCode();
        String getDescription();
        Set<String> getParameters();
        Map<String, Object> getAttributes();
    }

    /**
     * Represent a Result
     */
    interface Result extends Serializable {
        enum Status {
            OK,
            ERROR,
            UNSUPPORTED
        }

        Scope getScope();
        Status getStatus();
        List<Error> getErrors();
    }

    /**
     * TODO: document
     * @param parameters
     * @param scope
     * @return
     */
    Result verify(Scope scope, Map<String, Object> parameters);
}
