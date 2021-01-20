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
package org.apache.camel.support.startup;

import java.util.Arrays;

import org.apache.camel.StartupStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logging {@link org.apache.camel.spi.StartupStepRecorder} that outputs to log.
 */
public class LoggingStartupStepRecorder extends DefaultStartupStepRecorder {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingStartupStepRecorder.class);

    public LoggingStartupStepRecorder() {
        setEnabled(true);
    }

    protected void onEndStep(StartupStep step) {
        if (LOG.isInfoEnabled()) {
            long delta = System.currentTimeMillis() - step.getBeginTime();
            String pad = padString(step.getLevel());
            String out = String.format("%s", pad + step.getType());
            String out2 = String.format("%6s ms", delta);
            String out3 = String.format("%s(%s)", step.getDescription(), step.getName());
            LOG.info("{} : {} - {}", out2, out, out3);
        }
    }

    public static String padString(int level) {
        if (level == 0) {
            return "";
        } else {
            byte[] arr = new byte[level * 2];
            byte space = ' ';
            Arrays.fill(arr, space);
            return new String(arr);
        }
    }

    @Override
    public String toString() {
        return "logging";
    }
}
