package org.apache.camel.component.file.azure;

import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Objects;

import org.apache.camel.util.URISupport;

/**
 * SAS token
 * 
 * @see com.azure.storage.common.implementation.Constants.UrlConstants
 */
public final class Token {

    private String sv;
    private String ss;
    private String srt;
    private String sp;
    private String se;
    private String st;
    private String spr;
    private String sig;

    public void setSv(String sv) {
        this.sv = sv;
    }

    public void setSs(String ss) {
        this.ss = ss;
    }

    public void setSrt(String srt) {
        this.srt = srt;
    }

    public void setSp(String sp) {
        this.sp = sp;
    }

    public void setSe(String se) {
        this.se = se;
    }

    public void setSt(String st) {
        this.st = st;
    }

    public void setSpr(String spr) {
        this.spr = spr;
    }

    public void setSig(String sig) {
        this.sig = sig;
    }

    // sv=2021-12-02&ss=f&srt=o&sp=rwdlc&se=2023-05-05T19:27:05Z&st=2023-04-28T11:27:05Z&spr=https&sig=TCU0PcBjrxRbKOW%2FLA7HrPLISin6FXLNkRtLvmxkvhY%3D"
    // params in Azure order
    public String toURIQuery() {
        try {
            return String.format("sv=%s&ss=%s&srt=%s&sp=%s&se=%s&st=%s&spr=%s&sig=%s", e(sv), e(ss), e(srt), e(sp), e(se),
                    e(st), e(spr), e(sig));
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private String e(String value) throws URISyntaxException {
        // TODO a bit clumsy, check if Azure SDK does not have the needed encoder
        return URISupport.createQueryString(Collections.singletonMap("any", value)).substring(4)
                .replace("+", "%2B")
                .replace("%3A", ":");
    }

    @Override
    public int hashCode() {
        return Objects.hash(se, sig, sp, spr, srt, ss, st, sv);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Token other = (Token) obj;
        return Objects.equals(se, other.se) && Objects.equals(sig, other.sig)
                && Objects.equals(sp, other.sp) && Objects.equals(spr, other.spr)
                && Objects.equals(srt, other.srt) && Objects.equals(ss, other.ss)
                && Objects.equals(st, other.st) && Objects.equals(sv, other.sv);
    }

}
