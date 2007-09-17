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
package org.apache.camel.component.cxf.phase;


import java.util.SortedSet;
import org.apache.cxf.common.util.SortedArraySet;
import org.apache.cxf.phase.Phase;

public class RawMessagePhaseManagerImpl extends AbstractPhaseManagerImpl {

    protected SortedSet<Phase> createInPhases() {
        SortedSet<Phase> inPhases = new SortedArraySet<Phase>();

        int i = 0;
        
        inPhases.add(new Phase(Phase.RECEIVE, ++i * 1000));
        
        return inPhases;
    }

    protected SortedSet<Phase> createOutPhases() {
        SortedSet<Phase> outPhases = new SortedArraySet<Phase>();

        int i = 0;
        outPhases.add(new Phase(Phase.PREPARE_SEND, ++i * 1000));
        outPhases.add(new Phase(Phase.WRITE, ++i * 1000));
        outPhases.add(new Phase(Phase.SEND, ++i * 1000));
        outPhases.add(new Phase(Phase.PREPARE_SEND_ENDING, ++i * 1000));
        
        return outPhases;
    }
}

