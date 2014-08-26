package org.apache.camel.dataformat.bindy.format;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;

public class BigDecimalPatternFormat extends NumberPatternFormat<BigDecimal> {

    public void BigDecimalPatternFormat() {
    }

    public BigDecimalPatternFormat(String pattern, Locale locale, int precision, String rounding, String decimalSeparator, String groupingSeparator) {
        super(pattern, locale, precision, rounding, decimalSeparator, groupingSeparator);
    }

    @Override
    public BigDecimal parse(String string) throws Exception {
        if (getNumberFormat() != null) {
            Locale.setDefault(super.getLocale());
            DecimalFormat df = (DecimalFormat)getNumberFormat();
            df.setParseBigDecimal(true);
            BigDecimal bd = (BigDecimal)df.parse(string.trim());
            if(super.getPrecision() != -1) {
                bd = bd.setScale(super.getPrecision(), RoundingMode.valueOf(super.getRounding()));
            }
            Locale.getDefault();
            return bd;
        } else {
            return new BigDecimal(string.trim());
        }
    }
}
