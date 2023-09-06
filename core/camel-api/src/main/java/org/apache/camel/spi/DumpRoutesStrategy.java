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
package org.apache.camel.spi;

import org.apache.camel.StaticService;

/**
 * Strategy for dumping routes during startup dump all loaded routes (incl rests and route templates).
 */
public interface DumpRoutesStrategy extends StaticService {

    /**
     * Service factory key.
     */
    String FACTORY = "dump-routes";

    /**
     * Dump routes
     *
     * @param format xml or yaml
     */
    void dumpRoutes(String format);

    String getInclude();

    /**
     * Controls what to include in output.
     *
     * Possible values: all, routes, rests, routeConfigurations, routeTemplates, beans. Multiple values can be separated
     * by comma. Default is routes.
     */
    void setInclude(String include);

    boolean isResolvePlaceholders();

    /**
     * Whether to resolve property placeholders in the dumped output. Default is true.
     */
    void setResolvePlaceholders(boolean resolvePlaceholders);

    boolean isUriAsParameters();

    /**
     * When dumping to YAML format, then this option controls whether endpoint URIs should be expanded into a key/value
     * parameters.
     */
    void setUriAsParameters(boolean uriAsParameters);

    boolean isGeneratedIds();

    /**
     * Whether to include auto generated IDs in the dumped output. Default is false.
     */
    void setGeneratedIds(boolean generatedIds);

    boolean isLog();

    /**
     * Whether to log route dumps to Logger
     */
    void setLog(boolean log);

    String getOutput();

    /**
     * Whether to save route dumps to an output file.
     *
     * If the output is a filename, then all content is saved to this file. If the output is a directory name, then one
     * or more files are saved to the directory, where the names are based on the original source file names, or auto
     * generated names.
     */
    void setOutput(String output);

}
