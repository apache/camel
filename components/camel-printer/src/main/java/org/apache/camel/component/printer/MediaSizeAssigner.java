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
        if (size.equalsIgnoreCase("iso_a0")) {
            mediaSizeName = MediaSizeName.ISO_A0;
        } else if (size.equalsIgnoreCase("iso_a1")) {
            mediaSizeName = MediaSizeName.ISO_A1;
        } else if (size.equalsIgnoreCase("iso_a2")) {
            mediaSizeName = MediaSizeName.ISO_A2;
        } else if (size.equalsIgnoreCase("iso_a3")) {
            mediaSizeName = MediaSizeName.ISO_A3;
        } else if (size.equalsIgnoreCase("iso_a4")) {
            mediaSizeName = MediaSizeName.ISO_A4;
        } else if (size.equalsIgnoreCase("iso_a5")) {
            mediaSizeName = MediaSizeName.ISO_A5;
        } else if (size.equalsIgnoreCase("iso_a6")) {
            mediaSizeName = MediaSizeName.ISO_A6;
        } else if (size.equalsIgnoreCase("iso_a7")) {
            mediaSizeName = MediaSizeName.ISO_A7;
        } else if (size.equalsIgnoreCase("iso_a8")) {
            mediaSizeName = MediaSizeName.ISO_A8;
        } else if (size.equalsIgnoreCase("iso_a9")) {
            mediaSizeName = MediaSizeName.ISO_A9;
        } else if (size.equalsIgnoreCase("iso_a10")) {
            mediaSizeName = MediaSizeName.ISO_A10;
        } else if (size.equalsIgnoreCase("iso_b0")) {
            mediaSizeName = MediaSizeName.ISO_B0;
        } else if (size.equalsIgnoreCase("iso_b1")) {
            mediaSizeName = MediaSizeName.ISO_B1;
        } else if (size.equalsIgnoreCase("iso_b2")) {
            mediaSizeName = MediaSizeName.ISO_B2;
        } else if (size.equalsIgnoreCase("iso_b3")) {
            mediaSizeName = MediaSizeName.ISO_B3;
        } else if (size.equalsIgnoreCase("iso_b4")) {
            mediaSizeName = MediaSizeName.ISO_B4;
        } else if (size.equalsIgnoreCase("iso_b5")) {
            mediaSizeName = MediaSizeName.ISO_B5;
        } else if (size.equalsIgnoreCase("iso_b6")) {
            mediaSizeName = MediaSizeName.ISO_B6;
        } else if (size.equalsIgnoreCase("iso_b7")) {
            mediaSizeName = MediaSizeName.ISO_B7;
        } else if (size.equalsIgnoreCase("iso_b8")) {
            mediaSizeName = MediaSizeName.ISO_B8;
        } else if (size.equalsIgnoreCase("iso_b9")) {
            mediaSizeName = MediaSizeName.ISO_B9;
        } else if (size.equalsIgnoreCase("iso_b10")) {
            mediaSizeName = MediaSizeName.ISO_B10;
        } else if (size.equalsIgnoreCase("iso_c0")) {
            mediaSizeName = MediaSizeName.ISO_C0;
        } else if (size.equalsIgnoreCase("iso_c1")) {
            mediaSizeName = MediaSizeName.ISO_C1;
        } else if (size.equalsIgnoreCase("iso_c2")) {
            mediaSizeName = MediaSizeName.ISO_C2;
        } else if (size.equalsIgnoreCase("iso_c3")) {
            mediaSizeName = MediaSizeName.ISO_C3;
        } else if (size.equalsIgnoreCase("iso_c4")) {
            mediaSizeName = MediaSizeName.ISO_C4;
        } else if (size.equalsIgnoreCase("iso_c5")) {
            mediaSizeName = MediaSizeName.ISO_C5;
        } else if (size.equalsIgnoreCase("iso_c6")) {
            mediaSizeName = MediaSizeName.ISO_C6;
        }
        return mediaSizeName;
    }
    
    public MediaSizeName selectMediaSizeNameJIS(String size) {
        
        if (size.equalsIgnoreCase("jis_b0")) {
            mediaSizeName = MediaSizeName.JIS_B0;
        } else if (size.equalsIgnoreCase("jis_b1")) {
            mediaSizeName = MediaSizeName.JIS_B1;
        } else if (size.equalsIgnoreCase("jis_b2")) {
            mediaSizeName = MediaSizeName.JIS_B2;
        } else if (size.equalsIgnoreCase("jis_b3")) {
            mediaSizeName = MediaSizeName.JIS_B3;
        } else if (size.equalsIgnoreCase("jis_b4")) {
            mediaSizeName = MediaSizeName.JIS_B4;
        } else if (size.equalsIgnoreCase("jis_b5")) {
            mediaSizeName = MediaSizeName.JIS_B5;
        } else if (size.equalsIgnoreCase("jis_b6")) {
            mediaSizeName = MediaSizeName.JIS_B6;
        } else if (size.equalsIgnoreCase("jis_b7")) {
            mediaSizeName = MediaSizeName.JIS_B7;
        } else if (size.equalsIgnoreCase("jis_b8")) {
            mediaSizeName = MediaSizeName.JIS_B8;
        } else if (size.equalsIgnoreCase("jis_b9")) {
            mediaSizeName = MediaSizeName.JIS_B9;
        } else if (size.equalsIgnoreCase("jis_b10")) {
            mediaSizeName = MediaSizeName.JIS_B10;
        } 
        
        return mediaSizeName;
    }

    public MediaSizeName selectMediaSizeNameNA(String size) {
        if (size.equalsIgnoreCase("na_letter")) {
            mediaSizeName = MediaSizeName.NA_LETTER;
        } else if (size.equalsIgnoreCase("na_legal")) {
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
        } else if (size.equalsIgnoreCase("japanese_postcard")) {
            mediaSizeName = MediaSizeName.JAPANESE_POSTCARD;
        } else if (size.equalsIgnoreCase("oufuko_postcard")) {
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
        } else if (size.equalsIgnoreCase("iso_designated_long")) {
            mediaSizeName = MediaSizeName.ISO_DESIGNATED_LONG;
        } else if (size.equalsIgnoreCase("italian_envelope")) {
            mediaSizeName = MediaSizeName.ITALY_ENVELOPE;
        } else if (size.equalsIgnoreCase("monarch_envelope")) {
            mediaSizeName = MediaSizeName.MONARCH_ENVELOPE;
        } else if (size.equalsIgnoreCase("personal_envelope")) {
            mediaSizeName = MediaSizeName.PERSONAL_ENVELOPE;
        } else if (size.equalsIgnoreCase("na_number_9_envelope")) {
            mediaSizeName = MediaSizeName.NA_NUMBER_9_ENVELOPE;
        } else if (size.equalsIgnoreCase("na_number_10_envelope")) {
            mediaSizeName = MediaSizeName.NA_NUMBER_10_ENVELOPE;
        } else if (size.equalsIgnoreCase("na_number_11_envelope")) {
            mediaSizeName = MediaSizeName.NA_NUMBER_11_ENVELOPE;
        } else if (size.equalsIgnoreCase("na_number_12_envelope")) {
            mediaSizeName = MediaSizeName.NA_NUMBER_12_ENVELOPE;
        } else if (size.equalsIgnoreCase("na_number_14_envelope")) {
            mediaSizeName = MediaSizeName.NA_NUMBER_14_ENVELOPE;
        } else if (size.equalsIgnoreCase("na_6x9_envelope")) {
            mediaSizeName = MediaSizeName.NA_6X9_ENVELOPE;
        } else if (size.equalsIgnoreCase("na_7x9_envelope")) {
            mediaSizeName = MediaSizeName.NA_7X9_ENVELOPE;
        } else if (size.equalsIgnoreCase("na_9x11_envelope")) {
            mediaSizeName = MediaSizeName.NA_9X11_ENVELOPE;
        } else if (size.equalsIgnoreCase("na_9x12_envelope")) {
            mediaSizeName = MediaSizeName.NA_9X12_ENVELOPE;
        } else if (size.equalsIgnoreCase("na_10x13_envelope")) {
            mediaSizeName = MediaSizeName.NA_10X13_ENVELOPE;
        } else if (size.equalsIgnoreCase("na_10x14_envelope")) {
            mediaSizeName = MediaSizeName.NA_10X14_ENVELOPE;
        } else if (size.equalsIgnoreCase("na_10x15_envelope")) {
            mediaSizeName = MediaSizeName.NA_10X15_ENVELOPE;
        } else if (size.equalsIgnoreCase("na_5x7")) {
            mediaSizeName = MediaSizeName.NA_5X7;
        } else if (size.equalsIgnoreCase("na_8x10")) {
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
        } else if (size.equalsIgnoreCase("japanese_postcard")) {
            mediaSizeName = MediaSizeName.JAPANESE_POSTCARD;
        } else if (size.equalsIgnoreCase("oufuko_postcard")) {
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
        } else if (size.equalsIgnoreCase("iso_designated_long")) {
            mediaSizeName = MediaSizeName.ISO_DESIGNATED_LONG;
        } else if (size.equalsIgnoreCase("italian_envelope")) {
            mediaSizeName = MediaSizeName.ITALY_ENVELOPE;
        } else if (size.equalsIgnoreCase("monarch_envelope")) {
            mediaSizeName = MediaSizeName.MONARCH_ENVELOPE;
        } else if (size.equalsIgnoreCase("personal_envelope")) {
            mediaSizeName = MediaSizeName.PERSONAL_ENVELOPE;
        }
        
        return mediaSizeName;
    
    }
}
