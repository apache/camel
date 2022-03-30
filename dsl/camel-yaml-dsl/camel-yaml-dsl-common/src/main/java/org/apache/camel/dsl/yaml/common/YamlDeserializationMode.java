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
package org.apache.camel.dsl.yaml.common;

public enum YamlDeserializationMode {

    /**
     * This option configure the deserialization engine to strictly respect the model definition.
     *
     * </p>
     * As example, a Split step is expected to have it's own steps to process the result of the split.
     *
     * <pre>
     * {@code
     * - from:
     *     uri: "direct:a"
     *     steps:
     *       - split:
     *            tokenize: \n"
     *          steps:
     *            - log: "${body}"
     * }
     * </pre>
     */
    CLASSIC,

    /**
     * Mimics the Java Dsl.
     * </p>
     * When the deserializer is configured to use this mode, a route can be defined using a syntax that is closed to the
     * Java DSL, as example, the following Java route:
     *
     * <pre>
     * {@code
     * from("direct:a")
     *     .split().tokenize("\n"))
     *     .log("${body}");
     * }
     * </pre>
     *
     * Can be represented by the following YAML:
     *
     * <pre>
     * {@code
     * - from:
     *     uri: "direct:a"
     *     steps:
     *       - split:
     *            tokenize: \n"
     *       - log: "${body}"
     * }
     * </pre>
     *
     * As you may have noticed, there's no need to define the split's specific steps as the subsequent log processor is
     * automatically added to the step's outputs.
     * </p>
     * See https://issues.apache.org/jira/browse/CAMEL-16504
     */
    FLOW;
}
