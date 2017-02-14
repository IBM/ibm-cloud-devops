package draplugin.dra;

import hudson.model.Action;

/**
 * Created by lix on 8/29/16.
 */
public class BuildPublisherAction implements Action {

    private final String link;

    public BuildPublisherAction(String link) {
        this.link = link;
    }

    public String getLink() {
        return link;
    }

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return null;
    }

    @Override
    public String getUrlName() {
        return null;
    }
}
