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


public class PayloadPhaseManagerImplTest extends TestCase {

 
    public void testGetInPhases() throws Exception {
        PhaseManager pm = new PayloadPhaseManagerImpl();
        SortedSet<Phase> pl = pm.getInPhases();
        assertNotNull(pl);
        
        assertEquals(Phase.RECEIVE, pl.first().getName());
        assertEquals(Phase.READ, pl.last().getName());
    }


    public void testGetOutPhases() throws Exception {
        PhaseManager pm = new PayloadPhaseManagerImpl();
        SortedSet<Phase> pl = pm.getOutPhases();
        assertNotNull(pl);

        assertEquals(Phase.PREPARE_SEND, pl.first().getName());

        boolean hasWritePhase = false;
        for (Phase p : pl) {
            
            if (Phase.WRITE.equals(p.getName())) {
                hasWritePhase = true;
                break;
            }
        }

        assertTrue(hasWritePhase);
        assertEquals(Phase.PREPARE_SEND_ENDING, pl.last().getName());
    }
}
