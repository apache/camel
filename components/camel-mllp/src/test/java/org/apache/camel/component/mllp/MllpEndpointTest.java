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

package org.apache.camel.component.mllp;

import org.junit.Before;

/**
 * Tests for the MllpEndpoint class.
 */
public class MllpEndpointTest {
    static final String MSH_SEGMENT = "MSH|^~\\&|0|90100053675|JCAPS|CC|20131125122938|RISMD|ORM|28785|D|2.3";

    // @formatter:off
    static final String REMAINING_SEGMENTS =
        "PID|1||4507626^^^MRN^MRN||RAD VALIDATE^ROBERT||19650916|M||U|1818 UNIVERSITY AVE^^MADISON^WI^53703^USA^^^||(608)251-9999|||M|||579-85-3510||| " + '\r'
            + "PV1||OUTPATIENT|NMPCT^^^WWNMD^^^^^^^DEPID||||011463^ZARAGOZA^EDWARD^J.^^^^^EPIC^^^^PROVID|011463^ZARAGOZA^EDWARD^J.^^^^^EPIC^^^^PROVID"
            +     "|||||||||||90100053686|SELF||||||||||||||||||||||||201311251218|||||||V" + '\r'
            + "ORC|RE|9007395^EPC|9007395^EPC||Final||^^^201311251221^201311251222^R||201311251229|RISMD^RADIOLOGY^RADIOLOGIST^^|||SMO PET^^^7044^^^^^SMO PET CT||||||||||||||||I" + '\r'
            + "OBR|1|9007395^EPC|9007395^EPC|IMG7118^PET CT LIMITED CHEST W CONTRAST^IMGPROC^^PET CT CHEST||20131125|||||Ancillary Pe|||||||NMPCT|MP2 NM INJ01^MP2 NM INJECTION ROOM 01^PROVID"
            +     "|||201311251229||NM|Final||^^^201311251221^201311251222^R||||^test|E200003^RADIOLOGY^RESIDENT^^^^^^EPIC^^^^PROVID"
            +     "|812644^RADIOLOGY^GENERIC^ATTENDING 1^^^^^EPIC^^^^PROVID~000043^RADIOLOGY^RADIOLOGISTTWO^^^^^^EPIC^^^^PROVID|U0058489^SWAIN^CYNTHIA^LEE^||201311251245" + '\r'
            + "OBX|1|ST|&GDT|1|[11/25/2013 12:28:14 PM - PHYS, FIFTYFOUR]50||||||Final||||" + '\r' + '\n';
    // @formatter:on

    static final String TEST_MESSAGE = MSH_SEGMENT + '\r' + REMAINING_SEGMENTS;

    MllpEndpoint instance;

    @Before
    public void setUp() throws Exception {
        instance = new MllpEndpoint("mllp://dummy", new MllpComponent(), new MllpConfiguration());
    }

}