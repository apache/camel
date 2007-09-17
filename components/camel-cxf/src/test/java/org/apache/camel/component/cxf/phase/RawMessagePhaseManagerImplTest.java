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

import junit.framework.TestCase;

import org.apache.cxf.phase.Phase;
import org.apache.cxf.phase.PhaseManager;


public class RawMessagePhaseManagerImplTest extends TestCase {

    
    public void testGetInPhases() throws Exception {
        PhaseManager pm = new RawMessagePhaseManagerImpl();
        SortedSet<Phase> pl = pm.getInPhases();
        assertNotNull(pl);
        assertEquals(1, pl.size());
        assertEquals(Phase.RECEIVE, pl.first().getName());
    }

    
    public void testGetOutPhases() throws Exception {
        PhaseManager pm = new RawMessagePhaseManagerImpl();
        SortedSet<Phase> pl = pm.getOutPhases();
        assertNotNull(pl);
        assertEquals(4, pl.size());
        
        Object[] phaseArray;
        phaseArray = pl.toArray();
        assertEquals(Phase.PREPARE_SEND, ((Phase)phaseArray[0]).getName());
        assertEquals(Phase.WRITE, ((Phase)phaseArray[1]).getName());
        assertEquals(Phase.SEND, ((Phase)phaseArray[2]).getName());
        assertEquals(Phase.PREPARE_SEND_ENDING, ((Phase)phaseArray[3]).getName());
    }
}
