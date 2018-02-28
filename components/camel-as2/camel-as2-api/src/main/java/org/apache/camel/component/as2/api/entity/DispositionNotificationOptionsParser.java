package org.apache.camel.component.as2.api.entity;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import org.apache.http.ParseException;
import org.apache.http.message.ParserCursor;
import org.apache.http.message.TokenParser;
import org.apache.http.util.Args;
import org.apache.http.util.CharArrayBuffer;

public class DispositionNotificationOptionsParser {

    public final static DispositionNotificationOptionsParser INSTANCE = new DispositionNotificationOptionsParser();

    private final static String SIGNED_RECEIPT_PROTOCOL_PARAM_NAME = "signed-receipt-protocol";
    private final static String SIGNED_RECEIPT_PROTOCOL_PARAM_VALUE = "pkcs7-signature";
    private final static String SIGNED_RECEIPT_MICALG_PARAM_NAME = "signed-receipt-micalg";

    private final static char PARAM_VALUE_DELIMITER = ',';
    private final static char ELEM_DELIMITER = ';';
    private final static char PARAM_NAME_DELIMITER = '=';

    private static final BitSet PARAM_NAME_DELIMS = TokenParser.INIT_BITSET(PARAM_NAME_DELIMITER);
    private static final BitSet VALUE_DELIMS = TokenParser.INIT_BITSET(PARAM_VALUE_DELIMITER, ELEM_DELIMITER);

    public static DispositionNotificationOptions parseDispositionNotificationOptions(final String value,
                                                                                     DispositionNotificationOptionsParser parser)
            throws ParseException {
        Args.notNull(value, "Value");

        final CharArrayBuffer buffer = new CharArrayBuffer(value.length());
        buffer.append(value);
        final ParserCursor cursor = new ParserCursor(0, value.length());
        return (parser != null ? parser : DispositionNotificationOptionsParser.INSTANCE)
                .parseDispositionNotificationOptions(buffer, cursor);
    }

    private final TokenParser tokenParser;

    public DispositionNotificationOptionsParser() {
        this.tokenParser = TokenParser.INSTANCE;
    }

    public DispositionNotificationOptions parseDispositionNotificationOptions(final CharArrayBuffer buffer,
                                                                              final ParserCursor cursor) {
        Args.notNull(buffer, "Char array buffer");
        Args.notNull(cursor, "Parser cursor");
        final int indexFrom = cursor.getPos();
        final int indexTo = cursor.getUpperBound();

        try {
            final String signedReceiptProtocolParamName = tokenParser.parseToken(buffer, cursor, PARAM_NAME_DELIMS);
            if (cursor.atEnd() || buffer.charAt(cursor.getPos()) != PARAM_NAME_DELIMITER
                    || !signedReceiptProtocolParamName.equals(SIGNED_RECEIPT_PROTOCOL_PARAM_NAME)) {
                throw new ParseException(
                        "Invalid disposition notification options: " + buffer.substring(indexFrom, indexTo));
            }
            cursor.updatePos(cursor.getPos() + 1);

            final String protocolImportanceString = tokenParser.parseToken(buffer, cursor, VALUE_DELIMS);
            Importance protocolImportance = Importance.get(protocolImportanceString);
            if (cursor.atEnd() || buffer.charAt(cursor.getPos()) != PARAM_VALUE_DELIMITER
                    || protocolImportance == null) {
                throw new ParseException(
                        "Invalid disposition notification options: " + buffer.substring(indexFrom, indexTo));
            }
            cursor.updatePos(cursor.getPos() + 1);

            String protocolSymbol = tokenParser.parseToken(buffer, cursor, VALUE_DELIMS);
            if (cursor.atEnd() || buffer.charAt(cursor.getPos()) != ELEM_DELIMITER
                    || !protocolSymbol.equals(SIGNED_RECEIPT_PROTOCOL_PARAM_VALUE)) {
                throw new ParseException(
                        "Invalid disposition notification options: " + buffer.substring(indexFrom, indexTo));
            }
            cursor.updatePos(cursor.getPos() + 1);

            final String signedReceiptMicalgParamName = tokenParser.parseToken(buffer, cursor, PARAM_NAME_DELIMS);
            if (cursor.atEnd() || buffer.charAt(cursor.getPos()) != PARAM_NAME_DELIMITER
                    || !signedReceiptMicalgParamName.equals(SIGNED_RECEIPT_MICALG_PARAM_NAME)) {
                throw new ParseException(
                        "Invalid disposition notification options: " + buffer.substring(indexFrom, indexTo));
            }
            cursor.updatePos(cursor.getPos() + 1);

            final String micalgImportanceString = tokenParser.parseToken(buffer, cursor, VALUE_DELIMS);
            Importance micalgImportance = Importance.get(micalgImportanceString);
            if (cursor.atEnd() || buffer.charAt(cursor.getPos()) != PARAM_VALUE_DELIMITER || micalgImportance == null) {
                throw new ParseException(
                        "Invalid disposition notification options: " + buffer.substring(indexFrom, indexTo));
            }
            cursor.updatePos(cursor.getPos() + 1);

            List<String> micAlgorithmIds = new ArrayList<String>();
            while (!cursor.atEnd()) {
                String micAlgId = tokenParser.parseToken(buffer, cursor, VALUE_DELIMS);
                micAlgorithmIds.add(micAlgId);
            }

            return new DispositionNotificationOptions(protocolImportance, protocolSymbol, micalgImportance,
                    micAlgorithmIds.toArray(new String[0]));

        } catch (IndexOutOfBoundsException e) {
            throw new ParseException(
                    "Invalid disposition notification options: " + buffer.substring(indexFrom, indexTo));
        }

    }

}
