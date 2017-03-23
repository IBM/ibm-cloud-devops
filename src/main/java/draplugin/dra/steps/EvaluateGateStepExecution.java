package draplugin.dra.steps;

import draplugin.dra.EvaluateGate;
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

/**
 * Created by lix on 3/23/17.
 */
public class EvaluateGateStepExecution extends AbstractSynchronousNonBlockingStepExecution<Void> {
    @Inject
    private transient EvaluateGateStep step;

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
        System.out.println("Running gate step");

        String orgName = Util.isNullOrEmpty(step.getOrgName()) ? envVars.get("IBM_CLOUD_DEVOPS_ORG") : step.getOrgName();
        String applicationName =  Util.isNullOrEmpty(step.getApplicationName()) ? envVars.get("IBM_CLOUD_DEVOPS_APP_NAME") : step.getApplicationName();
        String toolchainName = Util.isNullOrEmpty(step.getToolchainId()) ? envVars.get("IBM_CLOUD_DEVOPS_TOOLCHAIN_ID") : step.getToolchainId();
        String username = envVars.get("IBM_CLOUD_DEVOPS_CREDS_USR");
        String password = envVars.get("IBM_CLOUD_DEVOPS_CREDS_PSW");
        Boolean willDisrupt = false;
        if (!Util.isNullOrEmpty(step.getForceDecision()) && step.getForceDecision().toLowerCase().equals("true")) {
            willDisrupt = true;
        }

        EvaluateGate evaluateGate = new EvaluateGate(
                step.getPolicy(),
                orgName,
                applicationName,
                toolchainName,
                step.getEnvironment(),
                username,
                password,
                willDisrupt);
        evaluateGate.perform(build, launcher, listener);
        return null;
    }
}
