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

package org.apache.camel.component.printer;

import javax.print.attribute.standard.MediaSizeName;

public class MediaSizeAssigner {
    private MediaSizeName mediaSizeName;
    
    public MediaSizeName selectMediaSizeNameISO(String size) {         
        if (size.equalsIgnoreCase("iso-a0")) {
            mediaSizeName = MediaSizeName.ISO_A0;
        } else if (size.equalsIgnoreCase("iso-a1")) {
            mediaSizeName = MediaSizeName.ISO_A1;
        } else if (size.equalsIgnoreCase("iso-a2")) {
            mediaSizeName = MediaSizeName.ISO_A2;
        } else if (size.equalsIgnoreCase("iso-a3")) {
            mediaSizeName = MediaSizeName.ISO_A3;
        } else if (size.equalsIgnoreCase("iso-a4")) {
            mediaSizeName = MediaSizeName.ISO_A4;
        } else if (size.equalsIgnoreCase("iso-a5")) {
            mediaSizeName = MediaSizeName.ISO_A5;
        } else if (size.equalsIgnoreCase("iso-a6")) {
            mediaSizeName = MediaSizeName.ISO_A6;
        } else if (size.equalsIgnoreCase("iso-a7")) {
            mediaSizeName = MediaSizeName.ISO_A7;
        } else if (size.equalsIgnoreCase("iso-a8")) {
            mediaSizeName = MediaSizeName.ISO_A8;
        } else if (size.equalsIgnoreCase("iso-a9")) {
            mediaSizeName = MediaSizeName.ISO_A9;
        } else if (size.equalsIgnoreCase("iso-a10")) {
            mediaSizeName = MediaSizeName.ISO_A10;
        } else if (size.equalsIgnoreCase("iso-b0")) {
            mediaSizeName = MediaSizeName.ISO_B0;
        } else if (size.equalsIgnoreCase("iso-b1")) {
            mediaSizeName = MediaSizeName.ISO_B1;
        } else if (size.equalsIgnoreCase("iso-b2")) {
            mediaSizeName = MediaSizeName.ISO_B2;
        } else if (size.equalsIgnoreCase("iso-b3")) {
            mediaSizeName = MediaSizeName.ISO_B3;
        } else if (size.equalsIgnoreCase("iso-b4")) {
            mediaSizeName = MediaSizeName.ISO_B4;
        } else if (size.equalsIgnoreCase("iso-b5")) {
            mediaSizeName = MediaSizeName.ISO_B5;
        } else if (size.equalsIgnoreCase("iso-b6")) {
            mediaSizeName = MediaSizeName.ISO_B6;
        } else if (size.equalsIgnoreCase("iso-b7")) {
            mediaSizeName = MediaSizeName.ISO_B7;
        } else if (size.equalsIgnoreCase("iso-b8")) {
            mediaSizeName = MediaSizeName.ISO_B8;
        } else if (size.equalsIgnoreCase("iso-b9")) {
            mediaSizeName = MediaSizeName.ISO_B9;
        } else if (size.equalsIgnoreCase("iso-b10")) {
            mediaSizeName = MediaSizeName.ISO_B10;
        } else if (size.equalsIgnoreCase("iso-c0")) {
            mediaSizeName = MediaSizeName.ISO_C0;
        } else if (size.equalsIgnoreCase("iso-c1")) {
            mediaSizeName = MediaSizeName.ISO_C1;
        } else if (size.equalsIgnoreCase("iso-c2")) {
            mediaSizeName = MediaSizeName.ISO_C2;
        } else if (size.equalsIgnoreCase("iso-c3")) {
            mediaSizeName = MediaSizeName.ISO_C3;
        } else if (size.equalsIgnoreCase("iso-c4")) {
            mediaSizeName = MediaSizeName.ISO_C4;
        } else if (size.equalsIgnoreCase("iso-c5")) {
            mediaSizeName = MediaSizeName.ISO_C5;
        } else if (size.equalsIgnoreCase("iso-c6")) {
            mediaSizeName = MediaSizeName.ISO_C6;
        }
        return mediaSizeName;
    }
    
    public MediaSizeName selectMediaSizeNameJIS(String size) {
        
        if (size.equalsIgnoreCase("jis-b0")) {
            mediaSizeName = MediaSizeName.JIS_B0;
        } else if (size.equalsIgnoreCase("jis-b1")) {
            mediaSizeName = MediaSizeName.JIS_B1;
        } else if (size.equalsIgnoreCase("jis-b2")) {
            mediaSizeName = MediaSizeName.JIS_B2;
        } else if (size.equalsIgnoreCase("jis-b3")) {
            mediaSizeName = MediaSizeName.JIS_B3;
        } else if (size.equalsIgnoreCase("jis-b4")) {
            mediaSizeName = MediaSizeName.JIS_B4;
        } else if (size.equalsIgnoreCase("jis-b5")) {
            mediaSizeName = MediaSizeName.JIS_B5;
        } else if (size.equalsIgnoreCase("jis-b6")) {
            mediaSizeName = MediaSizeName.JIS_B6;
        } else if (size.equalsIgnoreCase("jis-b7")) {
            mediaSizeName = MediaSizeName.JIS_B7;
        } else if (size.equalsIgnoreCase("jis-b8")) {
            mediaSizeName = MediaSizeName.JIS_B8;
        } else if (size.equalsIgnoreCase("jis-b9")) {
            mediaSizeName = MediaSizeName.JIS_B9;
        } else if (size.equalsIgnoreCase("jis-b10")) {
            mediaSizeName = MediaSizeName.JIS_B10;
        } 
        
        return mediaSizeName;
    }

    public MediaSizeName selectMediaSizeNameNA(String size) {
        if (size.equalsIgnoreCase("na-letter")) {
            mediaSizeName = MediaSizeName.NA_LETTER;
        } else if (size.equalsIgnoreCase("na-legal")) {
            mediaSizeName = MediaSizeName.NA_LEGAL;
        } else if (size.equalsIgnoreCase("executive")) {
            mediaSizeName = MediaSizeName.EXECUTIVE;
        } else if (size.equalsIgnoreCase("ledger")) {
            mediaSizeName = MediaSizeName.LEDGER;
        } else if (size.equalsIgnoreCase("tabloid")) {
            mediaSizeName = MediaSizeName.TABLOID;
        } else if (size.equalsIgnoreCase("invoice")) {
            mediaSizeName = MediaSizeName.INVOICE;
        } else if (size.equalsIgnoreCase("folio")) {
            mediaSizeName = MediaSizeName.FOLIO;
        } else if (size.equalsIgnoreCase("quarto")) {
            mediaSizeName = MediaSizeName.QUARTO;
        } else if (size.equalsIgnoreCase("japanese-postcard")) {
            mediaSizeName = MediaSizeName.JAPANESE_POSTCARD;
        } else if (size.equalsIgnoreCase("oufuko-postcard")) {
            mediaSizeName = MediaSizeName.JAPANESE_DOUBLE_POSTCARD;
        } else if (size.equalsIgnoreCase("a")) {
            mediaSizeName = MediaSizeName.A;
        } else if (size.equalsIgnoreCase("b")) {
            mediaSizeName = MediaSizeName.B;
        } else if (size.equalsIgnoreCase("c")) {
            mediaSizeName = MediaSizeName.C;
        } else if (size.equalsIgnoreCase("d")) {
            mediaSizeName = MediaSizeName.D;
        } else if (size.equalsIgnoreCase("e")) {
            mediaSizeName = MediaSizeName.E;
        } else if (size.equalsIgnoreCase("iso-designated-long")) {
            mediaSizeName = MediaSizeName.ISO_DESIGNATED_LONG;
        } else if (size.equalsIgnoreCase("italian-envelope")) {
            mediaSizeName = MediaSizeName.ITALY_ENVELOPE;
        } else if (size.equalsIgnoreCase("monarch-envelope")) {
            mediaSizeName = MediaSizeName.MONARCH_ENVELOPE;
        } else if (size.equalsIgnoreCase("personal-envelope")) {
            mediaSizeName = MediaSizeName.PERSONAL_ENVELOPE;
        } else if (size.equalsIgnoreCase("na-number-9-envelope")) {
            mediaSizeName = MediaSizeName.NA_NUMBER_9_ENVELOPE;
        } else if (size.equalsIgnoreCase("na-number-10-envelope")) {
            mediaSizeName = MediaSizeName.NA_NUMBER_10_ENVELOPE;
        } else if (size.equalsIgnoreCase("na-number-11-envelope")) {
            mediaSizeName = MediaSizeName.NA_NUMBER_11_ENVELOPE;
        } else if (size.equalsIgnoreCase("na-number-12-envelope")) {
            mediaSizeName = MediaSizeName.NA_NUMBER_12_ENVELOPE;
        } else if (size.equalsIgnoreCase("na-number-14-envelope")) {
            mediaSizeName = MediaSizeName.NA_NUMBER_14_ENVELOPE;
        } else if (size.equalsIgnoreCase("na-6x9-envelope")) {
            mediaSizeName = MediaSizeName.NA_6X9_ENVELOPE;
        } else if (size.equalsIgnoreCase("na-7x9-envelope")) {
            mediaSizeName = MediaSizeName.NA_7X9_ENVELOPE;
        } else if (size.equalsIgnoreCase("na-9x11-envelope")) {
            mediaSizeName = MediaSizeName.NA_9X11_ENVELOPE;
        } else if (size.equalsIgnoreCase("na-9x12-envelope")) {
            mediaSizeName = MediaSizeName.NA_9X12_ENVELOPE;
        } else if (size.equalsIgnoreCase("na-10x13-envelope")) {
            mediaSizeName = MediaSizeName.NA_10X13_ENVELOPE;
        } else if (size.equalsIgnoreCase("na-10x14-envelope")) {
            mediaSizeName = MediaSizeName.NA_10X14_ENVELOPE;
        } else if (size.equalsIgnoreCase("na-10x15-envelope")) {
            mediaSizeName = MediaSizeName.NA_10X15_ENVELOPE;
        } else if (size.equalsIgnoreCase("na-5x7")) {
            mediaSizeName = MediaSizeName.NA_5X7;
        } else if (size.equalsIgnoreCase("na-8x10")) {
            mediaSizeName = MediaSizeName.NA_8X10;
        } else {
            mediaSizeName = MediaSizeName.NA_LETTER;
        }
        
        return mediaSizeName;
    }
    
    public MediaSizeName selectMediaSizeNameOther(String size) {
        if (size.equalsIgnoreCase("executive")) {
            mediaSizeName = MediaSizeName.EXECUTIVE;
        } else if (size.equalsIgnoreCase("ledger")) {
            mediaSizeName = MediaSizeName.LEDGER;
        } else if (size.equalsIgnoreCase("tabloid")) {
            mediaSizeName = MediaSizeName.TABLOID;
        } else if (size.equalsIgnoreCase("invoice")) {
            mediaSizeName = MediaSizeName.INVOICE;
        } else if (size.equalsIgnoreCase("folio")) {
            mediaSizeName = MediaSizeName.FOLIO;
        } else if (size.equalsIgnoreCase("quarto")) {
            mediaSizeName = MediaSizeName.QUARTO;
        } else if (size.equalsIgnoreCase("japanese-postcard")) {
            mediaSizeName = MediaSizeName.JAPANESE_POSTCARD;
        } else if (size.equalsIgnoreCase("oufuko-postcard")) {
            mediaSizeName = MediaSizeName.JAPANESE_DOUBLE_POSTCARD;
        } else if (size.equalsIgnoreCase("a")) {
            mediaSizeName = MediaSizeName.A;
        } else if (size.equalsIgnoreCase("b")) {
            mediaSizeName = MediaSizeName.B;
        } else if (size.equalsIgnoreCase("c")) {
            mediaSizeName = MediaSizeName.C;
        } else if (size.equalsIgnoreCase("d")) {
            mediaSizeName = MediaSizeName.D;
        } else if (size.equalsIgnoreCase("e")) {
            mediaSizeName = MediaSizeName.E;
        } else if (size.equalsIgnoreCase("iso-designated-long")) {
            mediaSizeName = MediaSizeName.ISO_DESIGNATED_LONG;
        } else if (size.equalsIgnoreCase("italian-envelope")) {
            mediaSizeName = MediaSizeName.ITALY_ENVELOPE;
        } else if (size.equalsIgnoreCase("monarch-envelope")) {
            mediaSizeName = MediaSizeName.MONARCH_ENVELOPE;
        } else if (size.equalsIgnoreCase("personal-envelope")) {
            mediaSizeName = MediaSizeName.PERSONAL_ENVELOPE;
        }
        
        return mediaSizeName;
    
    }
}
