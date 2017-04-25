package com.ibm.devops.dra.steps;

import hudson.Extension;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;

/**
 * Created by lix on 3/22/17.
 */
public class PublishDeployStep extends AbstractStepImpl {
    // optional form fields from UI
    private String applicationName;
    private String orgName;
    private String credentialsId;
    private String toolchainId;

    // required parameters to support pipeline script
    private String result;
    private String environment;
    private String appUrl;

    @DataBoundConstructor
    public PublishDeployStep(String result, String environment, String appUrl) {
        this.environment = environment;
        this.appUrl = appUrl;
        this.result = result;
    }

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

    public String getEnvironment() {
        return environment;
    }

    public String getAppUrl() {
        return appUrl;
    }

    public String getToolchainId() {
        return toolchainId;
    }

    public String getResult() {
        return result;
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() { super(PublishDeployStepExecution.class); }

        @Override
        public String getFunctionName() {
            return "publishDeployRecord";
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Publish deploy record to IBM Cloud DevOps";
        }
    }
}
