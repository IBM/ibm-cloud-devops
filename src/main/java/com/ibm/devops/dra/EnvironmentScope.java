package com.ibm.devops.dra;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Created by lix on 12/12/16.
 */
public class EnvironmentScope extends AbstractDescribableImpl<EnvironmentScope> {
    private boolean isBuild;
    private boolean isAll;
    private boolean isDeploy;
    private String branchName;
    private String envName;

    @DataBoundConstructor
    public EnvironmentScope(String value, String branchName, String envName) {
        switch (value) {
            case "build":
                this.isBuild = true;
                this.isDeploy = false;
                this.isAll = false;
                break;
            case "deploy":
                this.isDeploy = true;
                this.isBuild = false;
                this.isAll = false;
                break;
            default:
                this.isAll = true;
                this.isBuild = false;
                this.isDeploy = false;
                break;
        }

        this.branchName = branchName;
        this.envName = envName;
    }

    public boolean isBuild() {
        return isBuild;
    }

    public boolean isAll() {
        return isAll;
    }

    public boolean isDeploy() {
        return isDeploy;
    }

    public String getBranchName() {
        return branchName;
    }

    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }

    public String getEnvName() {
        return envName;
    }

    public void setEnvName(String envName) {
        this.envName = envName;
    }


    @Extension
    public static class DescriptorImpl extends Descriptor<EnvironmentScope> {
        @Override
        public String getDisplayName() {
            return "DevOps Insight Test Environment Scope";
        }
    }
}
