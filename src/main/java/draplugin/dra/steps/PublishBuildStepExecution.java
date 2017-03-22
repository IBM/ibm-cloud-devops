package draplugin.dra.steps;

import draplugin.dra.PublishBuild;
import draplugin.dra.Util;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;

import javax.inject.Inject;

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
        System.out.println("Running publish build step");

        String orgName = envVars.get("IBM_CLOUD_DEVOPS_ORG");
        String applicationName = envVars.get("IBM_CLOUD_DEVOPS_APP_NAME");
        String toolchainName = envVars.get("IBM_CLOUD_DEVOPS_TOOLCHAIN_ID");
        String username = envVars.get("IBM_CLOUD_DEVOPS_CREDS_USR");
        String password = envVars.get("IBM_CLOUD_DEVOPS_CREDS_PSW");

        PublishBuild publishBuild = new PublishBuild(
                step.getResult(),
                step.getGitRepo(),
                step.getGitBranch(),
                step.getGitCommit(),
                orgName,
                applicationName,
                toolchainName,
                username,
                password);
        publishBuild.perform(build, ws, launcher, listener);
        return null;
    }
}
