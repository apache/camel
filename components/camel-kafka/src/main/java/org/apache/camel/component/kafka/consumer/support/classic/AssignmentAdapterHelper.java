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
package org.apache.camel.component.kafka.consumer.support.classic;

import org.apache.camel.component.kafka.KafkaConfiguration;
import org.apache.camel.component.kafka.SeekPolicy;
import org.apache.camel.spi.StateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class AssignmentAdapterHelper {

    private static final NoOpPartitionAssignmentAdapter NO_OP_ASSIGNMENT_ADAPTER = new NoOpPartitionAssignmentAdapter();
    private static final Logger LOG = LoggerFactory.getLogger(AssignmentAdapterHelper.class);

    private AssignmentAdapterHelper() {
    }

    public static PartitionAssignmentAdapter resolveBuiltinResumeAdapters(KafkaConfiguration configuration) {
        LOG.debug("No resume strategy was provided ... checking for built-ins ...");
        StateRepository<String, String> offsetRepository = configuration.getOffsetRepository();
        SeekPolicy seekTo = configuration.getSeekTo();

        if (offsetRepository != null) {
            LOG.info("Using resume from offset strategy");
            return new OffsetPartitionAssignmentAdapter(offsetRepository);
        } else if (seekTo != null) {
            LOG.info("Using resume from seek policy strategy with seeking from {}", seekTo);
            return new SeekPolicyPartitionAssignmentAdapter(seekTo);
        }

        LOG.info("Using NO-OP resume strategy");
        return NO_OP_ASSIGNMENT_ADAPTER;
    }
}
