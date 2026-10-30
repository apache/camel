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
package org.apache.camel.dsl.jbang.export.run;

/**
 * A run that ended before the integration was started, such as a build that failed.
 */
final class EndedIntegration implements RunningIntegration {

    private final int exitCode;

    EndedIntegration(int exitCode) {
        this.exitCode = exitCode;
    }

    @Override
    public long pid() {
        return 0;
    }

    @Override
    public int waitFor() {
        return exitCode;
    }

    @Override
    public void stop() {
        // nothing runs
    }
}
