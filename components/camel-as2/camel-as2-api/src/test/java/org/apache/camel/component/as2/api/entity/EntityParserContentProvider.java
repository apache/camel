package org.apache.camel.component.as2.api.entity;

public class EntityParserContentProvider {

    public static String dispositionNotificationReportContent(String contentLineEnding) {
        return """
                \r
                ------=_Part_56_1672293592.1028122454656\r
                Content-Type: text/plain\r
                Content-Transfer-Encoding: 7bit\r
                \r
                MDN for -_CR
                 Message ID: <200207310834482A70BF63@\\"~~foo~~\\">_CR
                  From: "\\"  as2Name  \\""_CR
                  To: "0123456780000" Received on: 2002-07-31 at 09:34:14 (EDT)_CR
                 Status: processed_CR
                 Comment: This is not a guarantee that the message has_CR
                  been completely processed or &understood by the receiving_CR
                  translator_CR
                \r
                ------=_Part_56_1672293592.1028122454656\r
                Content-Type: message/disposition-notification\r
                Content-Transfer-Encoding: 7bit\r
                \r
                Reporting-UA: AS2 Server\r
                MDN-Gateway: dns; example.com\r
                Original-Recipient: rfc822; 0123456780000\r
                Final-Recipient: rfc822; 0123456780000\r
                Original-Message-ID: <200207310834482A70BF63@\\"~~foo~~\\">\r
                Disposition: automatic-action/MDN-sent-automatically;\r
                  processed/warning: you're awesome\r
                Failure: oops-a-failure\r
                Error: oops-an-error\r
                Warning: oops-a-warning\r
                Received-content-MIC: 7v7F++fQaNB1sVLFtMRp+dF+eG4=, sha1\r
                \r
                ------=_Part_56_1672293592.1028122454656--\r
                """
                .replaceAll("_CR", contentLineEnding);
    }

    public static String dispositionNotificationReportContentUnfolded(String contentLineEnding) {
        return """
                \r
                ------=_Part_56_1672293592.1028122454656\r
                Content-Type: text/plain\r
                Content-Transfer-Encoding: 7bit\r
                \r
                MDN for -_CR
                 Message ID: <200207310834482A70BF63@\\"~~foo~~\\">_CR
                  From: "\\"  as2Name  \\""_CR
                  To: "0123456780000""
                  Received on: 2002-07-31 at 09:34:14 (EDT)_CR
                 Status: processed_CR
                 Comment: This is not a guarantee that the message has_CR
                  been completely processed or &understood by the receiving_CR
                  translator_CR
                \r
                ------=_Part_56_1672293592.1028122454656\r
                Content-Type: message/disposition-notification\r
                Content-Transfer-Encoding: 7bit\r
                \r
                Reporting-UA: AS2 Server\r
                MDN-Gateway: dns; example.com\r
                Original-Recipient: rfc822; 0123456780000\r
                Final-Recipient: rfc822; 0123456780000\r
                Original-Message-ID: <200207310834482A70BF63@\\"~~foo~~\\">\r
                Disposition: automatic-action/MDN-sent-automatically; rocessed/warning: you're awesome\r
                Failure: oops-a-failure\r
                Error: oops-an-error\r
                Warning: oops-a-warning\r
                Received-content-MIC: 7v7F++fQaNB1sVLFtMRp+dF+eG4=, sha1\r
                \r
                ------=_Part_56_1672293592.1028122454656--\r
                """
                .replaceAll("_CR", contentLineEnding);
    }

    public static String textPlainContent(String contentLineEnding) {
        return """
                MDN for -_CR
                 Message ID: <200207310834482A70BF63@\\"~~foo~~\\">_CR
                  From: "\\"  as2Name  \\""_CR
                  To: "0123456780000" Received on: 2002-07-31 at 09:34:14 (EDT)_CR
                 Status: processed_CR
                 Comment: This is not a guarantee that the message has_CR
                  been completely processed or &understood by the receiving_CR
                  translator_CR
                \r
                ------=_Part_56_1672293592.1028122454656--\r
                """
                .replaceAll("_CR", contentLineEnding);
    }

    public static String expectedTextPlainContent(String contentLineEnding) {

        return """
                MDN for -_CR
                 Message ID: <200207310834482A70BF63@\\"~~foo~~\\">_CR
                  From: "\\"  as2Name  \\""_CR
                  To: "0123456780000" Received on: 2002-07-31 at 09:34:14 (EDT)_CR
                 Status: processed_CR
                 Comment: This is not a guarantee that the message has_CR
                  been completely processed or &understood by the receiving_CR
                  translator_CR
                """
                .replaceAll("_CR", contentLineEnding);
    }

    public static String dispositionNotificationContent(String contentLineEnding) {
        return """
                Reporting-UA: AS2 Server_CR
                MDN-Gateway: dns; example.com_CR
                Original-Recipient: rfc822; 0123456780000_CR
                Final-Recipient: rfc822; 0123456780000_CR
                Original-Message-ID: <200207310834482A70BF63@\\"~~foo~~\\">_CR
                Disposition: automatic-action/MDN-sent-automatically;_CR
                  processed/warning: you're awesome_CR
                Failure: oops-a-failure_CR
                Error: oops-an-error_CR
                Warning: oops-a-warning_CR
                Received-content-MIC: 7v7F++fQaNB1sVLFtMRp+dF+eG4=, sha1_CR
                \r
                ------=_Part_56_1672293592.1028122454656--\r
                """
                .replaceAll("_CR", contentLineEnding);
    }
}