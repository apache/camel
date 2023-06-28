package org.apache.camel.component.file.azure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings("static-method")
public class FilesURIStringsTests {

    @Test
    void encodeTokenValueShouldEncodeBase64PlusSlashAndPadding() throws Exception {
        // e.g. for the sig base64 param on SAS token the encoding style must encode '+', '/', '='
        assertEquals("%2B%2Fa%3D", FilesURIStrings.encodeTokenValue("+/a="));
    }

    @Test
    void encodeTokenValueShouldPreserveTimeSeparator() throws Exception {
        // e.g. for the se param on SAS token the encoding style must preserve ':'
        assertEquals("11:55:01", FilesURIStrings.encodeTokenValue("11:55:01"));
    }

}
