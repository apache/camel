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
package org.apache.camel.component.extension.verifier;

import org.apache.camel.component.extension.ComponentVerifierExtension;
import org.apache.camel.component.extension.ComponentVerifierExtension.VerificationError.Attribute;
import org.apache.camel.component.extension.ComponentVerifierExtension.VerificationError.Code;
import org.apache.camel.component.extension.ComponentVerifierExtension.VerificationError.ExceptionAttribute;
import org.apache.camel.component.extension.ComponentVerifierExtension.VerificationError.GroupAttribute;
import org.apache.camel.component.extension.ComponentVerifierExtension.VerificationError.HttpAttribute;
import org.apache.camel.component.extension.ComponentVerifierExtension.VerificationError.StandardCode;

/**
 * Package visible helper class holding implementation classes for
 * constant like error code and attributes in {@link  ComponentVerifierExtension.VerificationError}
 */
public final class ComponentVerifierExtensionHelper {

    /**
     * Custom class for error codes
     */
    public static class ErrorCode implements Code {

        private final String name;

        public ErrorCode(String name) {
            if (name == null) {
                throw new IllegalArgumentException("Name of an error code must not be null");
            }
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Code)) {
                return false;
            }

            Code errorCode = (Code) o;

            return name.equals(errorCode.name());
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        @Override
        public String toString() {
            return name();
        }
    }

    public static class ErrorAttribute implements Attribute {

        private final String name;

        public ErrorAttribute(String name) {
            if (name == null) {
                throw new IllegalArgumentException("Name of an error attribute must not be null");
            }
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }


        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Attribute)) {
                return false;
            }

            Attribute that = (Attribute) o;

            return name.equals(that.name());
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        @Override
        public String toString() {
            return name();
        }
    }

    // ===========================================================================================================
    // Helper classes for implementing the constants in ComponentVerifier:

    public static class StandardErrorCode extends ErrorCode implements StandardCode {
        public StandardErrorCode(String name) {
            super(name);
        }
    }

    public static class ExceptionErrorAttribute extends ErrorAttribute implements ExceptionAttribute {
        public ExceptionErrorAttribute(String name) {
            super(name);
        }
    }

    public static class HttpErrorAttribute extends ErrorAttribute implements HttpAttribute {
        public HttpErrorAttribute(String name) {
            super(name);
        }
    }

    public static class GroupErrorAttribute extends ErrorAttribute implements GroupAttribute {
        public GroupErrorAttribute(String name) {
            super(name);
        }
    }
}
