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

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit.rule.mllp.MllpClientResource;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Rule;
import org.junit.Test;

public class MllpTcpServerCharsetTest extends CamelTestSupport {
    static final String TEST_MESSAGE = 
        "MSH|^~\\&|KinetDx|UCLA Health System|||201801301506||ORU^R01|18030543772221|P|2.3^^||||||ISO_IR 100|" + '\r' 
        + "PID|1||1117922||TESTER^MARY||19850627|F" + '\r'
        + "OBR|1||55510818|ECH10^TRANSTHORACIC ECHO ADULT COMPLETE^IMGPROC|||20180126103542|||||||||029137^LEIBZON^ROMAN^^^^^^EPIC^^^^PROVID|||||Y|20180130150612|||F"
        +     "|||||||029137^Leibzon^Roman^^MD^^^^EPIC^^^^PROVID" + '\r'
        + "DG1|1|I10|^I10^ HTN (essential)^I10" + '\r'
        + "DG1|2|I10|R94.31^Abnormal EKG^I10" + '\r'
        + "OBX|1|FT|&GDT||  Thousand Oaks Cardiology||||||F" + '\r'
        + "OBX|2|FT|&GDT|| 100 Moody Court, Suite 200||||||F" + '\r'
        + "OBX|3|FT|&GDT||  Thousand Oaks, CA 91360||||||F" + '\r'
        + "OBX|4|FT|&GDT||    Phone: 805-418-3500||||||F" + '\r'
        + "OBX|5|FT|&GDT|| ||||||F" + '\r'
        + "OBX|6|FT|&GDT||TRANSTHORACIC ECHOCARDIOGRAM REPORT||||||F" + '\r'
        + "OBX|7|FT|&GDT|| ||||||F" + '\r'
        + "OBX|8|FT|&GDT||Patient Name:              Date of Exam:   1/26/2018||||||F" + '\r'
        + "OBX|9|FT|&GDT||Medical Rec #:                    Accession #:         ||||||F" + '\r'
        + "OBX|10|FT|&GDT||Date of Birth:                   Height:         74 in||||||F" + '\r'
        + "OBX|11|FT|&GDT||Age:                             Weight:         230 lbs||||||F" + '\r'
        + "OBX|12|FT|&GDT||Gender:                          BSA:            2.31 m²||||||F" + '\r'
        + "OBX|13|FT|&GDT||Referring Physician: 029137 ROMAN LEIBZON Blood Pressure: /||||||F" + '\r'
        + "OBX|14|FT|&GDT||Diagnosis: I10- HTN (essential); R94.31-Abnormal EKG||||||F" + '\r'
        + "OBX|15|FT|&GDT|| ||||||F" + '\r'
        + "OBX|16|FT|&GDT||MEASUREMENTS:||||||F" + '\r'
        + "OBX|17|FT|&GDT||LVIDd (2D)     5.16 cm LVIDs (2D)   3.14 cm||||||F" + '\r'
        + "OBX|18|FT|&GDT||IVSd (2D)      0.93 cm LVPWd (2D)   1.10 cm||||||F" + '\r'
        + "OBX|19|FT|&GDT||LA (2D)        4.00 cm Ao Root (2D) 3.00 cm||||||F" + '\r'
        + "OBX|20|FT|&GDT||FINDINGS:||||||F" + '\r'
        + "OBX|21|FT|&GDT||Left Ventricle: The left ventricular size is normal. Left ventricular wall thickness is normal. LV wall motion is normal. The ejection fraction by Simpson's "
        +     "Biplane method is 60 %. Normal LV diastolic function. MV deceleration time is 127 msec.||||||F" + '\r'
        + "OBX|22|FT|&GDT||MV E velocity is 0.77 m/s. MV A velocity is 0.56 m/s. E/A ratio is 1.36.||||||F" + '\r'
        + "OBX|23|FT|&GDT||Lateral E/e' ratio is 6.0. Medial E/e' ratio is 8.7.||||||F" + '\r'
        + "OBX|24|FT|&GDT||Left Atrium: The left atrium is mildly dilated in size. The LA Volume index is 30.8 ml/m².||||||F" + '\r'
        + "OBX|25|FT|&GDT||Right Atrium: The right atrium is normal in size. RA area is 17 cm2. RA volume is 42 ml.||||||F" + '\r'
        + "OBX|26|FT|&GDT||Right Ventricle: The right ventricular size is normal. Global RV systolic function is normal. TAPSE 24 mm. The RV free wall tissue Doppler S' wave measures 16.7 cm/s. "
        +     "The right ventricle basal diameter measures 26 mm. The right ventricle mid cavity measures 23 mm. The right ventricle longitudinal diameter measures 65 mm.||||||F" + '\r'
        + "OBX|27|FT|&GDT||Mitral Valve: Mitral annular calcification noted. Trace mitral valve regurgitation. There is no mitral stenosis.||||||F" + '\r'
        + "OBX|28|FT|&GDT||Aortic Valve: The aortic valve appears trileaflet. Trace aortic valve regurgitation. The LVOT velocity is 1.16 m/s. The peak aortic valve velocity is 1.19 m/s. "
        +     "No aortic valve stenosis.||||||F" + '\r'
        + "OBX|29|FT|&GDT||Tricuspid Valve: The tricuspid valve appears normal in structure. Trace tricuspid regurgitation is present. The peak velocity of TR is 2.55 m/s.||||||F" + '\r'
        + "OBX|30|FT|&GDT||Pulmonic Valve: Trivial pulmonary valve regurgitation. No evidence of pulmonary valve stenosis.||||||F" + '\r'
        + "OBX|31|FT|&GDT||Pericardium: There is no pericardial effusion.||||||F" + '\r'
        + "OBX|32|FT|&GDT||Aorta: The aortic root size is normal. The aortic valve annulus measures 25 mm. The sinus of Valsalva measures 33 mm. The sinotubular junction measures 30 mm. "
        +     "The proximal ascending aorta measures 30 mm.||||||F" + '\r'
        + "OBX|33|FT|&GDT||Pulmonary Artery: Based on the acceleration time in the RV outflow tract, the PA pressure is not likely to be elevated. The calculated pulmonary artery pressure "
        +     "(or right ventricular systolic pressure) is 29 mmHg, if the right atrial pressure is 3 mmHg. Normal PA systolic pressure.||||||F" + '\r'
        + "OBX|34|FT|&GDT||IVC: Normal inferior vena cava in diameter with respiratory variation consistent with normal right atrial pressure.||||||F" + '\r'
        + "OBX|35|FT|&GDT|| ||||||F" + '\r'
        + "OBX|36|FT|&GDT||IMPRESSION:||||||F" + '\r'
        + "OBX|37|FT|&GDT|| 1. Normal left ventricular size.||||||F" + '\r'
        + "OBX|38|FT|&GDT|| 2. The calculated ejection fraction (Simpson's) is 60 %.||||||F" + '\r'
        + "OBX|39|FT|&GDT|| 3. Normal LV diastolic function.||||||F" + '\r'
        + "OBX|40|FT|&GDT|| 4. Mildly dilated left atrium in size.||||||F" + '\r'
        + "OBX|41|FT|&GDT||029137 Roman Leibzon MD||||||F" + '\r'
        + "OBX|42|FT|&GDT||Electronically signed by 029137 Roman Leibzon MD on 1/30/2018 at 3:06:12 PM||||||F" + '\r'
        + "OBX|43|FT|&GDT|| ||||||F" + '\r'
        + "OBX|44|FT|&GDT||Sonographer: Liana Yenokyan||||||F" + '\r'
        + "OBX|45|FT|&GDT|| ||||||F" + '\r'
        + "OBX|46|FT|&GDT||*** Final ***||||||F\r";
        
    static final String TARGET_URI = "mock://target";

    @Rule
    public MllpClientResource mllpClient = new MllpClientResource();

    @EndpointInject(uri = TARGET_URI)
    MockEndpoint target;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        mllpClient.setMllpHost("localhost");
        mllpClient.setMllpPort(AvailablePortFinder.getNextAvailable());

        DefaultCamelContext context = (DefaultCamelContext) super.createCamelContext();

        context.setUseMDCLogging(true);
        context.setName(this.getClass().getSimpleName());

        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            String routeId = "mllp-sender";

            public void configure() {
                fromF("mllp://%d?receiveTimeout=1000&readTimeout=500&charsetName=ISO-IR-100", mllpClient.getMllpPort())
                    .log(LoggingLevel.INFO, routeId, "Sending Message")
                    .to(target);
            }
        };
    }


    @Test
    public void testReceiveMessageWithInvalidMsh18() throws Exception {
        target.expectedMinimumMessageCount(1);

        mllpClient.connect();

        mllpClient.sendMessageAndWaitForAcknowledgement(TEST_MESSAGE);

        assertMockEndpointsSatisfied(5, TimeUnit.SECONDS);
    }

    @Test
    public void testReceiveMessageWithValidMsh18() throws Exception {
        target.expectedMinimumMessageCount(1);

        mllpClient.connect();

        mllpClient.sendMessageAndWaitForAcknowledgement(TEST_MESSAGE.replace("ISO_IR 100", "ISO-IR-100"));

        assertMockEndpointsSatisfied(5, TimeUnit.SECONDS);
    }
}
