package draplugin.dra.steps;

import hudson.Extension;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;

/**
 * Created by charlie cox on 4/19/17.
 */
public class PublishSQStep extends AbstractStepImpl {
    // optional form fields from UI
    private String applicationName;
    private String orgName;
    private String credentialsId;
    private String toolchainId;
    private String environment;

    // required parameters
    private String SQHostURL;
    private String SQAuthToken;
    private String SQProjectKey;

    @DataBoundConstructor
    public PublishSQStep(String SQHostURL, String SQAuthToken, String SQProjectKey) {

        this.SQHostURL = SQHostURL;
        this.SQAuthToken = SQAuthToken;
        this.SQProjectKey = SQProjectKey;
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

    @DataBoundSetter
    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    @DataBoundSetter
    public void setSQHostURL(String SQHostURL) {
        this.SQHostURL = SQHostURL;
    }

    @DataBoundSetter
    public void setSQAuthToken(String SQAuthToken) {
        this.SQAuthToken = SQAuthToken;
    }


    @DataBoundSetter
    public void setSQProjectKey(String SQProjectKey) {
        this.SQProjectKey = SQProjectKey;
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

    public String getEnvironment() {
        return environment;
    }

    public String getSQHostURL() {
        return SQHostURL;
    }

    public String getSQAuthToken() {
        return SQAuthToken;
    }

    public String getSQProjectKey() {
        return SQProjectKey;
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() { super(PublishSQStepExecution.class); }

        @Override
        public String getFunctionName() {
            return "publishSQResults";
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Publish SonarQube test results to IBM Cloud DevOps";
        }
    }
}
