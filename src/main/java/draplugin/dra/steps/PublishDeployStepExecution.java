package draplugin.dra.steps;

import draplugin.dra.PublishBuild;
import draplugin.dra.PublishDeploy;
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
public class PublishDeployStepExecution extends AbstractSynchronousNonBlockingStepExecution<Void> {
    @Inject
    private transient PublishDeployStep step;

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

        PublishDeploy publishDeploy = new PublishDeploy(
                step.getEnvironment(),
                step.getAppUrl(),
                step.getResult(),
                toolchainName,
                applicationName,
                orgName,
                username,
                password);
        publishDeploy.perform(build, ws, launcher, listener);
        return null;
    }
}

