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
package org.apache.camel.component.chaos;

import java.util.function.Function;

import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.PropertyConfigurer;
import org.apache.camel.spi.PropertyConfigurerGetter;

/**
 * Whether the consumer of an endpoint keeps a message whose exchange failed, and reads it again, so the report shows
 * the failure as "kept by the consumer". It reads the options of the endpoint through the configurer of its component
 * (so an option set on the component counts too), without a dependency on the components:
 * <ul>
 * <li>{@code file}, {@code ftp}, {@code sftp}, {@code ftps}: without {@code moveFailed} (the rollback leaves the file,
 * and with {@code noop=true} takes it out of the idempotent repository).</li>
 * <li>{@code jms}, {@code activemq}, {@code activemq6}, {@code amqp}: with {@code transacted=true}, or with
 * {@code acknowledgementModeName=CLIENT_ACKNOWLEDGE} and {@code asyncConsumer=false}.</li>
 * <li>{@code kafka}: with {@code breakOnFirstError=true}.</li>
 * </ul>
 * The component is known by the scheme of the endpoint, so a component added under another name counts as any other.
 * Any other consumer, or an endpoint without a configurer, does not keep it. A stub that stands in for one of these
 * components ({@code camel run --stub}) keeps it by the options of its URI, as the component would, though the stub
 * does not read the message again (see {@link ChaosStubs}).
 */
final class ChaosConsumers {

    private ChaosConsumers() {
    }

    /**
     * Whether the consumer keeps the message of an exchange that was marked rollback only, without an exception: only
     * the JMS consumers (as for a failed one) roll their session back for it; the file consumers and Kafka look at the
     * exception only, and commit the message.
     */
    static boolean keepsRollbackOnly(Endpoint endpoint) {
        if (endpoint == null) {
            return false;
        }
        return switch (ChaosPatterns.scheme(endpoint.getEndpointUri())) {
            case "jms", "activemq", "activemq6", "amqp" -> keepsFailed(endpoint);
            default -> false;
        };
    }

    static boolean keepsFailed(Endpoint endpoint) {
        if (endpoint == null) {
            return false;
        }
        Function<String, Object> option = option(endpoint);
        if (option == null) {
            return false;
        }
        try {
            return keepsFailed(endpoint, option);
        } catch (RuntimeException e) {
            // an option that cannot be read: the report says "failed"
            return false;
        }
    }

    private static boolean keepsFailed(Endpoint endpoint, Function<String, Object> option) {
        return switch (ChaosPatterns.scheme(endpoint.getEndpointUri())) {
            case "file", "ftp", "sftp", "ftps" -> option.apply("moveFailed") == null;
            case "jms", "activemq", "activemq6", "amqp" -> isTrue(option.apply("transacted"))
                    || isClientAcknowledge(option) && !isTrue(option.apply("asyncConsumer"));
            case "kafka" -> isTrue(option.apply("breakOnFirstError"));
            default -> false;
        };
    }

    // acknowledgementModeName is the endpoint option (acknowledgementMode, the number, is not one)
    private static boolean isClientAcknowledge(Function<String, Object> option) {
        return "CLIENT_ACKNOWLEDGE".equals(text(option.apply("acknowledgementModeName")));
    }

    /**
     * How the options of the endpoint are read: through the configurer of its component, or from the URI of a stub, as
     * the stub component does not configure them; null when they cannot be read.
     */
    private static Function<String, Object> option(Endpoint endpoint) {
        if (ChaosStubs.stubbedScheme(endpoint) != null) {
            return ChaosStubs.uriOptions(endpoint)::get;
        }
        Component component = endpoint.getComponent();
        PropertyConfigurer configurer = component != null ? component.getEndpointPropertyConfigurer() : null;
        return configurer instanceof PropertyConfigurerGetter getter
                ? name -> getter.getOptionValue(endpoint, name, true) : null;
    }

    private static boolean isTrue(Object value) {
        return Boolean.TRUE.equals(ChaosFaults.toBoolean(value));
    }

    private static String text(Object value) {
        return value != null ? value.toString().trim() : null;
    }
}
