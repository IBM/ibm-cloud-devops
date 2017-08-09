package com.ibm.devops.dra.steps;

import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Created by lix on 8/8/17.
 */
abstract public class AbstractDevOpsStep extends AbstractStepImpl {
    private String applicationName;
    private String orgName;
    private String credentialsId;
    private String toolchainId;

    @DataBoundSetter
    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    @DataBoundSetter
    public void setOrgName(String orgName) {
        this.orgName = orgName;
    }

    @DataBoundSetter
    public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    @DataBoundSetter
    public void setToolchainId(String toolchainId) {
        this.toolchainId = toolchainId;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public String getOrgName() {
        return orgName;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public String getToolchainId() {
        return toolchainId;
    }

}
