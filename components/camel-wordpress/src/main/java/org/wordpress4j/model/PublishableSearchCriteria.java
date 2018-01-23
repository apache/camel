package org.wordpress4j.model;

import java.util.Date;
import java.util.List;

public abstract class PublishableSearchCriteria extends SearchCriteria {

    private static final long serialVersionUID = 9178680514805178843L;
    
    private Date after;
    private Date before;
    private List<Integer> author;
    private List<Integer> authorExclude;
    private List<Integer> offset;
    private List<String> slug;
    private PublishableStatus status;
    private Context context;

    public Date getAfter() {
        return after;
    }

    public void setAfter(Date after) {
        this.after = after;
    }

    public Date getBefore() {
        return before;
    }

    public void setBefore(Date before) {
        this.before = before;
    }

    public List<Integer> getAuthor() {
        return author;
    }

    public void setAuthor(List<Integer> author) {
        this.author = author;
    }

    public List<Integer> getAuthorExclude() {
        return authorExclude;
    }

    public void setAuthorExclude(List<Integer> authorExclude) {
        this.authorExclude = authorExclude;
    }

    public List<Integer> getOffset() {
        return offset;
    }

    public void setOffset(List<Integer> offset) {
        this.offset = offset;
    }

    public List<String> getSlug() {
        return slug;
    }

    public void setSlug(List<String> slug) {
        this.slug = slug;
    }

    public PublishableStatus getStatus() {
        return status;
    }

    public void setStatus(PublishableStatus status) {
        this.status = status;
    }

    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        this.context = context;
    }

}
