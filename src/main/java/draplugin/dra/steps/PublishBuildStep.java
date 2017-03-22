package draplugin.dra.steps;

import com.google.common.collect.ImmutableSet;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Node;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import java.util.Set;

/**
 * Created by lix on 3/21/17.
 */
public class PublishBuildStep extends AbstractStepImpl {
    // optional form fields from UI
    private String applicationName;
    private String orgName;
    private String credentialsId;
    private String toolchainId;

    // required parameters to support pipeline script
    private String result;
    private String gitRepo;
    private String gitBranch;
    private String gitCommit;

    @DataBoundConstructor
    public PublishBuildStep(String result, String gitRepo, String gitBranch, String gitCommit) {
        this.gitRepo = gitRepo;
        this.gitBranch = gitBranch;
        this.gitCommit = gitCommit;
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

    public String getGitRepo() {
        return gitRepo;
    }

    public String getGitBranch() {
        return gitBranch;
    }

    public String getGitCommit() {
        return gitCommit;
    }

    public String getToolchainId() {
        return toolchainId;
    }

    public String getResult() {
        return result;
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {


        public DescriptorImpl() { super(PublishBuildStepExecution.class); }

        @Override
        public String getFunctionName() {
            return "publishBuild";
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "publich build";
        }
    }
}
