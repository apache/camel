package org.apache.camel.component.file.azure;

import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.camel.util.URISupport;

/**
 * Azure Files account or service SAS token.
 * <p>
 * The user delegation SAS token is not specified for Azure Files.
 * 
 * @see com.azure.storage.common.implementation.Constants.UrlConstants
 * @see <a href="https://learn.microsoft.com/en-us/rest/api/storageservices/create-account-sas">Azure account SAS</a>
 * @see <a href="https://learn.microsoft.com/en-us/rest/api/storageservices/create-service-sas">Azure service SAS</a>
 */
final class FilesToken {

    private String sv;
    private String ss;
    private String srt;
    private String sp;
    private String se;
    private String st;
    private String spr;
    private String sig;
    private String si;  // service SAS only
    private String sr;  // service SAS only
    private String sdd; // service SAS only
    private String sip;

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

    public void setSi(String si) {
        this.si = si;
    }

    public void setSr(String sr) {
        this.sr = sr;
    }

    public void setSdd(String sdd) {
        this.sdd = sdd;
    }

    public void setSip(String sip) {
        this.sip = sip;
    }

    boolean isInvalid() {
        return sig == null || sv == null || se == null || !(isAccountTokenForFilesService() || isFilesServiceToken());
    }

    private boolean isAccountTokenForFilesService() {
        return ss != null && ss.contains("f");
    }

    private boolean isFilesServiceToken() {
        return sr != null && (sr.contains("f") || sr.contains("s"));
    }

    // sv=2021-12-02&ss=f&srt=o&sp=rwdlc&se=2023-05-05T19:27:05Z&st=2023-04-28T11:27:05Z&spr=https&sig=TCU0PcBjrxRbKOW%2FLA7HrPLISin6FXLNkRtLvmxkvhY%3D"
    // params in Azure order
    public String toURIQuery() {
        try {
            return Stream
                    .of(e("sv", sv), e("ss", ss), e("srt", srt), e("sp", sp), e("se", se), e("st", st),
                            e("spr", spr), e("sig", sig), e("si", si), e("sr", sr), e("sdd", sdd), e("sip", sip))
                    .collect(Collectors.joining("&"));
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private String e(String param, String value) throws URISyntaxException {
        if (value == null || value.isBlank()) {
            return "";
        }
        // TODO a bit clumsy, check if Azure SDK does not have the needed encoder
        return param + "=" + URISupport.createQueryString(Collections.singletonMap("any", value))
                .substring(4).replace("+", "%2B").replace("%3A", ":");
    }

    @Override
    public int hashCode() {
        return Objects.hash(sdd, se, si, sig, sip, sp, spr, sr, srt, ss, st, sv);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        FilesToken other = (FilesToken) obj;
        return Objects.equals(sdd, other.sdd) && Objects.equals(se, other.se)
                && Objects.equals(si, other.si) && Objects.equals(sig, other.sig)
                && Objects.equals(sip, other.sip) && Objects.equals(sp, other.sp)
                && Objects.equals(spr, other.spr) && Objects.equals(sr, other.sr)
                && Objects.equals(srt, other.srt) && Objects.equals(ss, other.ss)
                && Objects.equals(st, other.st) && Objects.equals(sv, other.sv);
    }

}
