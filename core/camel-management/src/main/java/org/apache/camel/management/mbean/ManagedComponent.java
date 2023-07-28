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
package org.apache.camel.management.mbean;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.camel.Component;
import org.apache.camel.ServiceStatus;
import org.apache.camel.StatefulService;
import org.apache.camel.api.management.ManagedInstance;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.ComponentVerifierExtension;
import org.apache.camel.api.management.mbean.ComponentVerifierExtension.Result;
import org.apache.camel.api.management.mbean.ComponentVerifierExtension.Result.Status;
import org.apache.camel.api.management.mbean.ComponentVerifierExtension.Scope;
import org.apache.camel.api.management.mbean.ComponentVerifierExtension.VerificationError;
import org.apache.camel.api.management.mbean.ComponentVerifierExtension.VerificationError.ExceptionAttribute;
import org.apache.camel.api.management.mbean.ComponentVerifierExtension.VerificationError.GroupAttribute;
import org.apache.camel.api.management.mbean.ComponentVerifierExtension.VerificationError.HttpAttribute;
import org.apache.camel.api.management.mbean.ComponentVerifierExtension.VerificationError.StandardCode;
import org.apache.camel.api.management.mbean.ManagedComponentMBean;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.support.HealthCheckComponent;
import org.apache.camel.util.CastUtils;

@ManagedResource(description = "Managed Component")
public class ManagedComponent implements ManagedInstance, ManagedComponentMBean {
    private final Component component;
    private final String name;

    public ManagedComponent(String name, Component component) {
        this.name = name;
        this.component = component;
    }

    public void init(ManagementStrategy strategy) {
        // do nothing
    }

    public Component getComponent() {
        return component;
    }

    @Override
    public String getComponentName() {
        return name;
    }

    @Override
    public String getState() {
        // must use String type to be sure remote JMX can read the attribute without requiring Camel classes.
        if (component instanceof StatefulService) {
            ServiceStatus status = ((StatefulService) component).getStatus();
            return status.name();
        }

        // assume started if not a ServiceSupport instance
        return ServiceStatus.Started.name();
    }

    @Override
    public String getCamelId() {
        return component.getCamelContext().getName();
    }

    @Override
    public String getCamelManagementName() {
        return component.getCamelContext().getManagementName();
    }

    @Override
    public Object getInstance() {
        return component;
    }

    @Override
    public boolean isHealthCheckSupported() {
        return component instanceof HealthCheckComponent;
    }

    @Override
    public boolean isHealthCheckConsumerEnabled() {
        if (component instanceof HealthCheckComponent) {
            return ((HealthCheckComponent) component).isHealthCheckConsumerEnabled();
        }
        return false;
    }

    @Override
    public boolean isHealthCheckProducerEnabled() {
        if (component instanceof HealthCheckComponent) {
            return ((HealthCheckComponent) component).isHealthCheckProducerEnabled();
        }
        return false;
    }

    @Override
    public boolean isVerifySupported() {
        return component.getExtension(org.apache.camel.component.extension.ComponentVerifierExtension.class).isPresent();
    }

    @Override
    public ComponentVerifierExtension.Result verify(String scope, Map<String, String> options) {
        try {
            org.apache.camel.component.extension.ComponentVerifierExtension.Scope scopeEnum
                    = org.apache.camel.component.extension.ComponentVerifierExtension.Scope.fromString(scope);
            Optional<org.apache.camel.component.extension.ComponentVerifierExtension> verifier
                    = component.getExtension(org.apache.camel.component.extension.ComponentVerifierExtension.class);
            if (verifier.isPresent()) {
                org.apache.camel.component.extension.ComponentVerifierExtension.Result result
                        = verifier.get().verify(scopeEnum, CastUtils.cast(options));
                String rstatus = result.getStatus().toString();
                String rscope = result.getScope().toString();
                return new ResultImpl(
                        Scope.valueOf(rscope), Status.valueOf(rstatus),
                        result.getErrors().stream().map(this::translate).toList());

            } else {
                return new ResultImpl(Scope.PARAMETERS, Status.UNSUPPORTED, Collections.emptyList());
            }
        } catch (IllegalArgumentException e) {
            return new ResultImpl(
                    Scope.PARAMETERS, Status.UNSUPPORTED, Collections.singletonList(
                            new VerificationErrorImpl(StandardCode.UNSUPPORTED_SCOPE, "Unsupported scope: " + scope)));
        }
    }

    private VerificationError translate(
            org.apache.camel.component.extension.ComponentVerifierExtension.VerificationError error) {
        return new VerificationErrorImpl(
                translate(error.getCode()), error.getDescription(),
                error.getParameterKeys(), translate(error.getDetails()));
    }

    private Map<VerificationError.Attribute, Object> translate(
            Map<org.apache.camel.component.extension.ComponentVerifierExtension.VerificationError.Attribute, Object> details) {
        return details.entrySet().stream().collect(Collectors.toMap(e -> translate(e.getKey()), Entry::getValue));
    }

    private VerificationError.Attribute translate(
            org.apache.camel.component.extension.ComponentVerifierExtension.VerificationError.Attribute attribute) {
        if (attribute
            == org.apache.camel.component.extension.ComponentVerifierExtension.VerificationError.GroupAttribute.GROUP_NAME) {
            return GroupAttribute.GROUP_NAME;
        } else if (attribute
                   == org.apache.camel.component.extension.ComponentVerifierExtension.VerificationError.GroupAttribute.GROUP_OPTIONS) {
            return GroupAttribute.GROUP_OPTIONS;
        } else if (attribute
                   == org.apache.camel.component.extension.ComponentVerifierExtension.VerificationError.HttpAttribute.HTTP_CODE) {
            return HttpAttribute.HTTP_CODE;
        } else if (attribute
                   == org.apache.camel.component.extension.ComponentVerifierExtension.VerificationError.HttpAttribute.HTTP_REDIRECT) {
            return HttpAttribute.HTTP_REDIRECT;
        } else if (attribute
                   == org.apache.camel.component.extension.ComponentVerifierExtension.VerificationError.HttpAttribute.HTTP_TEXT) {
            return HttpAttribute.HTTP_TEXT;
        } else if (attribute
                   == org.apache.camel.component.extension.ComponentVerifierExtension.VerificationError.ExceptionAttribute.EXCEPTION_CLASS) {
            return ExceptionAttribute.EXCEPTION_CLASS;
        } else if (attribute
                   == org.apache.camel.component.extension.ComponentVerifierExtension.VerificationError.ExceptionAttribute.EXCEPTION_INSTANCE) {
            return ExceptionAttribute.EXCEPTION_INSTANCE;
        } else if (attribute != null) {
            return VerificationError.asAttribute(attribute.getName());
        } else {
            return null;
        }
    }

    private VerificationError.Code translate(
            org.apache.camel.component.extension.ComponentVerifierExtension.VerificationError.Code code) {
        if (code
            == org.apache.camel.component.extension.ComponentVerifierExtension.VerificationError.StandardCode.AUTHENTICATION) {
            return StandardCode.AUTHENTICATION;
        } else if (code
                   == org.apache.camel.component.extension.ComponentVerifierExtension.VerificationError.StandardCode.EXCEPTION) {
            return StandardCode.EXCEPTION;
        } else if (code
                   == org.apache.camel.component.extension.ComponentVerifierExtension.VerificationError.StandardCode.INTERNAL) {
            return StandardCode.INTERNAL;
        } else if (code
                   == org.apache.camel.component.extension.ComponentVerifierExtension.VerificationError.StandardCode.MISSING_PARAMETER) {
            return StandardCode.MISSING_PARAMETER;
        } else if (code
                   == org.apache.camel.component.extension.ComponentVerifierExtension.VerificationError.StandardCode.UNKNOWN_PARAMETER) {
            return StandardCode.UNKNOWN_PARAMETER;
        } else if (code
                   == org.apache.camel.component.extension.ComponentVerifierExtension.VerificationError.StandardCode.ILLEGAL_PARAMETER) {
            return StandardCode.ILLEGAL_PARAMETER;
        } else if (code
                   == org.apache.camel.component.extension.ComponentVerifierExtension.VerificationError.StandardCode.ILLEGAL_PARAMETER_GROUP_COMBINATION) {
            return StandardCode.ILLEGAL_PARAMETER_GROUP_COMBINATION;
        } else if (code
                   == org.apache.camel.component.extension.ComponentVerifierExtension.VerificationError.StandardCode.ILLEGAL_PARAMETER_VALUE) {
            return StandardCode.ILLEGAL_PARAMETER_VALUE;
        } else if (code
                   == org.apache.camel.component.extension.ComponentVerifierExtension.VerificationError.StandardCode.INCOMPLETE_PARAMETER_GROUP) {
            return StandardCode.ILLEGAL_PARAMETER_GROUP_COMBINATION;
        } else if (code
                   == org.apache.camel.component.extension.ComponentVerifierExtension.VerificationError.StandardCode.UNSUPPORTED) {
            return StandardCode.UNSUPPORTED;
        } else if (code
                   == org.apache.camel.component.extension.ComponentVerifierExtension.VerificationError.StandardCode.UNSUPPORTED_SCOPE) {
            return StandardCode.UNSUPPORTED_SCOPE;
        } else if (code
                   == org.apache.camel.component.extension.ComponentVerifierExtension.VerificationError.StandardCode.UNSUPPORTED_COMPONENT) {
            return StandardCode.UNSUPPORTED_COMPONENT;
        } else if (code
                   == org.apache.camel.component.extension.ComponentVerifierExtension.VerificationError.StandardCode.GENERIC) {
            return StandardCode.GENERIC;
        } else if (code != null) {
            return VerificationError.asCode(code.getName());
        } else {
            return null;
        }
    }

    public static class VerificationErrorImpl implements VerificationError {
        private final Code code;
        private final String description;
        private final Set<String> parameterKeys;
        private final Map<Attribute, Object> details;

        public VerificationErrorImpl(Code code, String description) {
            this.code = code;
            this.description = description;
            this.parameterKeys = Collections.emptySet();
            this.details = Collections.emptyMap();
        }

        public VerificationErrorImpl(Code code, String description, Set<String> parameterKeys, Map<Attribute, Object> details) {
            this.code = code;
            this.description = description;
            this.parameterKeys = parameterKeys;
            this.details = details;
        }

        @Override
        public Code getCode() {
            return code;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public Set<String> getParameterKeys() {
            return parameterKeys;
        }

        @Override
        public Map<Attribute, Object> getDetails() {
            return details;
        }
    }

    public static class ResultImpl implements Result {
        private final Scope scope;
        private final Status status;
        private final List<VerificationError> errors;

        public ResultImpl(Scope scope, Status status, List<VerificationError> errors) {
            this.scope = scope;
            this.status = status;
            this.errors = errors;
        }

        @Override
        public Scope getScope() {
            return scope;
        }

        @Override
        public Status getStatus() {
            return status;
        }

        @Override
        public List<VerificationError> getErrors() {
            return errors;
        }
    }
}
