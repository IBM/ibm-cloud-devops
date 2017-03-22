package draplugin.dra.steps;

import hudson.Extension;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;

/**
 * Created by lix on 3/22/17.
 */
public class PublishTestStep extends AbstractStepImpl {
    // optional form fields from UI
    private String applicationName;
    private String orgName;
    private String credentialsId;
    private String toolchainId;
    private String environment;

    // required parameters
    private String type;
    private String fileLocation;

    @DataBoundConstructor
    public PublishTestStep(String type, String fileLocation) {
        this.type = type;
        this.fileLocation = fileLocation;
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

    public String getType() {
        return type;
    }

    public String getFileLocation() {
        return fileLocation;
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() { super(PublishTestStepExecution.class); }

        @Override
        public String getFunctionName() {
            return "publishTestResult";
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Publish test result to IBM Cloud DevOps";
        }
    }
}
