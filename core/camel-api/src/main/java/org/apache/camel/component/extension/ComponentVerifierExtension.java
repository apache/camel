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
package org.apache.camel.component.extension;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.Component;
import org.apache.camel.component.extension.ComponentVerifierExtensionHelper.ErrorAttribute;
import org.apache.camel.component.extension.ComponentVerifierExtensionHelper.ErrorCode;
import org.apache.camel.component.extension.ComponentVerifierExtensionHelper.ExceptionErrorAttribute;
import org.apache.camel.component.extension.ComponentVerifierExtensionHelper.GroupErrorAttribute;
import org.apache.camel.component.extension.ComponentVerifierExtensionHelper.HttpErrorAttribute;
import org.apache.camel.component.extension.ComponentVerifierExtensionHelper.StandardErrorCode;

/**
 * Defines the interface used for validating component/endpoint parameters. The central method of this
 * interface is {@link #verify(ComponentVerifierExtension.Scope, Map)} which takes a scope and a set of parameters which should be verified.
 * <p/>
 * The return value is a {@link ComponentVerifierExtension.Result} of the verification
 *
 */
public interface ComponentVerifierExtension extends ComponentExtension {

    /**
     * Verify the given parameters against a provided scope.
     *
     * <p>
     * The supported scopes are:
     * <ul>
     *   <li><strong>{@link ComponentVerifierExtension.Scope#PARAMETERS}</strong>: to validate that all the mandatory options are provided and syntactically correct.</li>
     *   <li><strong>{@link ComponentVerifierExtension.Scope#CONNECTIVITY}</strong>: to validate that the given options (i.e. credentials, addresses) are correct. Verifying with this
     *       scope typically implies reaching out to the backend via some sort of network connection.</li>
     * </ul>
     *
     * @param scope the scope of the verification
     * @param parameters the parameters to verify which are interpreted individually by each component verifier
     * @return the verification result
     */
    Result verify(Scope scope, Map<String, Object> parameters);

    /**
     * The result of a verification
     */
    interface Result extends Serializable {

        /**
         * Status of the verification
         */
        enum Status {
            /**
             * Verification succeeded
             */
            OK,
            /**
             * Error occurred during the verification
             */
            ERROR,
            /**
             * Verification is not supported. This can depend on the given scope.
             */
            UNSUPPORTED
        }

        /**
         * Scope of the verification. This is the scope given to the call to {@link #verify(Scope, Map)}  and
         * can be used for correlation.
         *
         * @return the scope against which the parameters have been validated.
         */
        Scope getScope();

        /**
         * Result of the validation as status. This should be the first datum to check after a verification
         * happened.
         *
         * @return the status
         */
        Status getStatus();

        /**
         * Collection of errors happened for the verification. This list is empty (but non null) if the verification
         * succeeded.
         *
         * @return a list of errors. Can be empty when verification was successful
         */
        List<VerificationError> getErrors();
    }

    /**
     * The scope defines how the parameters should be verified.
     */
    enum Scope {
        /**
         * Only validate the parameters for their <em>syntactic</em> soundness. Verifications in this scope should
         * be as fast as possible
         */
        PARAMETERS,

        /**
         * Reach out to the backend and verify that a connection can be established. This means, if the verification
         * in this scope succeeds, then it can safely be assumed that the component can be used.
         */
        CONNECTIVITY;

        /**
         * Get an instance of this scope from a string representation
         *
         * @param scope the scope as string, which can be in any case
         * @return the scope enum represented by this string
         */
        public static Scope fromString(String scope) {
            return Scope.valueOf(scope != null ? scope.toUpperCase() : null);
        }
    }

    // =============================================================================================

    /**
     * This interface represents a detailed error in case when the verification fails.
     */
    interface VerificationError extends Serializable {

        /**
         * The overall error code, which can be either a {@link StandardCode} or a custom code. It is
         * recommended to stick to the predefined standard codes
         *
         * @return the general error code.
         */
        Code getCode();

        /**
         * A human readable description of the error in plain english
         *
         * @return the error description (if available)
         */
        String getDescription();

        /**
         * A set of input parameter names which fails the verification. These are keys to the parameter provided
         * to {@link #verify(ComponentVerifierExtension.Scope, Map)}.
         *
         * @return the parameter names which are malformed and caused the failure of the validation
         */
        Set<String> getParameterKeys();

        /**
         * Details about the failed verification. The keys can be either predefined values
         * ({@link ExceptionAttribute}, {@link HttpAttribute}, {@link GroupAttribute}) or it can be free-form
         * custom keys specific to a component. The standard attributes are defined as enums in all uppercase (with
         * underscore as separator), custom attributes are supposed to be in all lower case (also with underscores
         * as separators)
         *
         * @return a number of key/value pair with additional information related to the verification.
         */
        Map<Attribute, Object> getDetails();

        /**
         * Get a single detail for a given attribute
         *
         * @param attribute the attribute to lookup
         * @return the detail value or null if no such attribute exists
         */
        default Object getDetail(Attribute attribute) {
            Map<Attribute, Object> details = getDetails();
            if (details != null) {
                return details.get(attribute);
            }
            return null;
        }

        /**
         * Get a single detail for a given attribute
         *
         * @param attribute the attribute to lookup
         * @return the detail value or null if no such attribute exists
         */
        default Object getDetail(String attribute) {
            return getDetail(asAttribute(attribute));
        }

        /**
         * Convert a string to an {@link Code}
         *
         * @param code the code to convert. It should be in all lower case (with
         *             underscore as a separator) to avoid overlap with {@link StandardCode}
         * @return error code
         */
        static Code asCode(String code) {
            return new ErrorCode(code);
        }

        /**
         * Convert a string to an {@link Attribute}
         *
         * @param attribute the string representation of an attribute to convert. It should be in all lower case (with
         *                  underscore as a separator) to avoid overlap with standard attributes like {@link ExceptionAttribute},
         *                  {@link HttpAttribute} or {@link GroupAttribute}
         * @return generated attribute
         */
        static Attribute asAttribute(String attribute) {
            return new ErrorAttribute(attribute);
        }

        /**
         * Interface defining an error code. This is implemented by the {@link StandardCode} but also
         * own code can be generated by implementing this interface. This is best done via {@link #asCode(String)}
         * If possible, the standard codes should be reused
         */
        interface Code extends Serializable {
            /**
             * Name of the code. All uppercase for standard codes, all lower case for custom codes.
             * Separator between two words is an underscore.
             *
             * @return code name
             */
            String name();

            /**
             * Bean style accessor to name.
             * This is required for framework like Jackson using bean convention for object serialization.
             *
             * @return code name
             */
            default String getName() {
                return name();
            }
        }

        /**
         * Standard set of error codes
         */
        interface StandardCode extends Code {
            /**
             * Authentication failed
             */
            StandardCode AUTHENTICATION = new StandardErrorCode("AUTHENTICATION");
            /**
             * An exception occurred
             */
            StandardCode EXCEPTION = new StandardErrorCode("EXCEPTION");
            /**
             * Internal error while performing the verification
             */
            StandardCode INTERNAL = new StandardErrorCode("INTERNAL");
            /**
             * A mandatory parameter is missing
             */
            StandardCode MISSING_PARAMETER = new StandardErrorCode("MISSING_PARAMETER");
            /**
             * A given parameter is not known to the component
             */
            StandardCode UNKNOWN_PARAMETER = new StandardErrorCode("UNKNOWN_PARAMETER");
            /**
             * A given parameter is illegal
             */
            StandardCode ILLEGAL_PARAMETER = new StandardErrorCode("ILLEGAL_PARAMETER");
            /**
             * A combination of parameters is illegal. See {@link VerificationError#getParameterKeys()} for the set
             * of affected parameters
             */
            StandardCode ILLEGAL_PARAMETER_GROUP_COMBINATION = new StandardErrorCode("ILLEGAL_PARAMETER_GROUP_COMBINATION");
            /**
             * A parameter <em>value</em> is not valid
             */
            StandardCode ILLEGAL_PARAMETER_VALUE = new StandardErrorCode("ILLEGAL_PARAMETER_VALUE");
            /**
             * A group of parameters is not complete in order to be valid
             */
            StandardCode INCOMPLETE_PARAMETER_GROUP = new StandardErrorCode("INCOMPLETE_PARAMETER_GROUP");
            /**
             * The verification is not supported
             */
            StandardCode UNSUPPORTED = new StandardErrorCode("UNSUPPORTED");
            /**
             * The requested {@link Scope} is not supported
             */
            StandardCode UNSUPPORTED_SCOPE = new StandardErrorCode("UNSUPPORTED_SCOPE");
            /**
             * The requested {@link Component} is not supported
             */
            StandardCode UNSUPPORTED_COMPONENT = new StandardErrorCode("UNSUPPORTED_COMPONENT");
            /**
             * Generic error which is explained in more details with {@link VerificationError#getDetails()}
             */
            StandardCode GENERIC = new StandardErrorCode("GENERIC");
        }

        /**
         * Interface defining an attribute which is a key for the detailed error messages. This is implemented by several
         * standard enums like {@link ExceptionAttribute}, {@link HttpAttribute} or {@link GroupAttribute} but can also
         * implemented for component specific details. This is best done via {@link #asAttribute(String)}
         * or using one of the other builder method in this error builder (like
         * {@link org.apache.camel.component.extension.verifier.ResultErrorBuilder#detail(String, Object)}
         * <p>
         * With respecting to name, the same rules as for {@link Code} apply: Standard attributes are all upper case with _
         * as separators, whereas custom attributes are lower case with underscore separators.
         */
        interface Attribute extends Serializable {
            /**
             * Name of the attribute. All uppercase for standard attributes and all lower case for custom attributes.
             * Separator between words is an underscore.
             *
             * @return attribute name
             */
            String name();

            /**
             * Bean style accessor to name;
             * This is required for framework like Jackson using bean convention for object serialization.
             *
             * @return attribute name
             */
            default String getName() {
                return name();
            }
        }

        /**
         * Attributes for details about an exception that was raised
         */
        interface ExceptionAttribute extends Attribute {
            /**
             * The exception object that has been thrown. Note that this can be a complex
             * object and can cause large content when e.g. serialized as JSON
             */
            ExceptionAttribute EXCEPTION_INSTANCE = new ExceptionErrorAttribute("EXCEPTION_INSTANCE");
            /**
             * The exception class
             */
            ExceptionAttribute EXCEPTION_CLASS = new ExceptionErrorAttribute("EXCEPTION_CLASS");
        }

        /**
         * HTTP related error details
         */
        interface HttpAttribute extends Attribute {
            /**
             * The erroneous HTTP code that occurred
             */
            HttpAttribute HTTP_CODE = new HttpErrorAttribute("HTTP_CODE");
            /**
             * HTTP response's body
             */
            HttpAttribute HTTP_TEXT = new HttpErrorAttribute("HTTP_TEXT");
            /**
             * If given as details, specifies that a redirect happened and the
             * content of this detail is the redirect URL
             */
            HttpAttribute HTTP_REDIRECT = new HttpErrorAttribute("HTTP_REDIRECT");
        }

        /**
         * Group related details
         */
        interface GroupAttribute extends Attribute {
            /**
             * Group name
             */
            GroupAttribute GROUP_NAME = new GroupErrorAttribute("GROUP_NAME");
            /**
             * Options for the group
             */
            GroupAttribute GROUP_OPTIONS = new GroupErrorAttribute("GROUP_OPTIONS");
        }
    }
}
