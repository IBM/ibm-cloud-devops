package draplugin.dra.steps;

import draplugin.dra.PublishBuild;
import draplugin.dra.Util;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;

import javax.inject.Inject;
import java.io.PrintStream;

/**
 * Created by lix on 3/21/17.
 */
public class PublishBuildStepExecution extends AbstractSynchronousNonBlockingStepExecution<Void> {
    @Inject
    private transient PublishBuildStep step;

    @StepContextParameter
    private transient TaskListener listener;
    @StepContextParameter
    private transient FilePath ws;
    @StepContextParameter
    private transient Launcher launcher;
    @StepContextParameter
    private transient Run build;
    @StepContextParameter
    private transient EnvVars envVars;

    @Override
    protected Void run() throws Exception {

        PrintStream printStream = listener.getLogger();

        String orgName = Util.isNullOrEmpty(step.getOrgName()) ? envVars.get("IBM_CLOUD_DEVOPS_ORG") : step.getOrgName();
        String applicationName =  Util.isNullOrEmpty(step.getApplicationName()) ? envVars.get("IBM_CLOUD_DEVOPS_APP_NAME") : step.getApplicationName();
        String toolchainName = Util.isNullOrEmpty(step.getToolchainId()) ? envVars.get("IBM_CLOUD_DEVOPS_TOOLCHAIN_ID") : step.getToolchainId();
        String username = envVars.get("IBM_CLOUD_DEVOPS_CREDS_USR");
        String password = envVars.get("IBM_CLOUD_DEVOPS_CREDS_PSW");

        //check all the required env vars
        if (!Util.allNotNullOrEmpty(orgName, applicationName,toolchainName, username, password)) {
            printStream.println("[IBM Cloud DevOps] Missing environment variables configurations, please specify all required environment variables in the pipeline");
            printStream.println("[IBM Cloud DevOps] Error: Failed to upload Build Record.");
            return null;
        }

        //check all the required parameters
        String result = step.getResult();
        String gitRepo = step.getGitRepo();
        String gitBranch = step.getGitBranch();
        String gitCommit = step.getGitCommit();
        if (!Util.allNotNullOrEmpty(result, gitRepo, gitBranch, gitCommit)) {
            printStream.println("[IBM Cloud DevOps] publishBuildRecord is missing required parameters, " +
                    "please make sure you specify \"result\", \"gitRepo\", \"gitBranch\", \"gitCommit\"");
            printStream.println("[IBM Cloud DevOps] Error: Failed to upload Build Record.");
            return null;
        }

        if (result.equals("SUCCESS") || result.equals("FAIL")) {
            PublishBuild publishBuild = new PublishBuild(
                    result,
                    gitRepo,
                    gitBranch,
                    gitCommit,
                    orgName,
                    applicationName,
                    toolchainName,
                    username,
                    password);
            publishBuild.perform(build, ws, launcher, listener);
        } else {
            printStream.println("[IBM Cloud DevOps] the \"result\" in the publishBuildRecord should be either \"PASS\" or \"FAIL\"");
        }

        return null;
    }
}
