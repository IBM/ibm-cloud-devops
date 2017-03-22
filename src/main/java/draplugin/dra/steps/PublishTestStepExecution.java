package draplugin.dra.steps;

import draplugin.dra.PublishBuild;
import draplugin.dra.PublishTest;
import draplugin.dra.Util;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;

import javax.inject.Inject;

/**
 * Created by lix on 3/22/17.
 */
public class PublishTestStepExecution extends AbstractSynchronousNonBlockingStepExecution<Void> {
    @Inject
    private transient PublishTestStep step;

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

        String orgName = Util.isNullOrEmpty(step.getOrgName()) ? envVars.get("IBM_CLOUD_DEVOPS_ORG") : step.getOrgName();
        String applicationName =  Util.isNullOrEmpty(step.getApplicationName()) ? envVars.get("IBM_CLOUD_DEVOPS_APP_NAME") : step.getApplicationName();
        String toolchainName = Util.isNullOrEmpty(step.getToolchainId()) ? envVars.get("IBM_CLOUD_DEVOPS_TOOLCHAIN_ID") : step.getToolchainId();
        String username = envVars.get("IBM_CLOUD_DEVOPS_CREDS_USR");
        String password = envVars.get("IBM_CLOUD_DEVOPS_CREDS_PSW");

        PublishTest publishTest = new PublishTest(
                step.getType(),
                step.getFileLocation(),
                step.getEnvironment(),
                orgName,
                applicationName,
                toolchainName,
                username,
                password);
        publishTest.perform(build, ws, launcher, listener);
        return null;
    }
}
