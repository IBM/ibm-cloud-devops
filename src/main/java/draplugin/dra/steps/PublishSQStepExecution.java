package draplugin.dra.steps;

import draplugin.dra.PublishSQ;
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
 * Created by charlie cox on 4/19/17.
 */
public class PublishSQStepExecution extends AbstractSynchronousNonBlockingStepExecution<Void> {
    @Inject
    private transient PublishSQStep step;

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

        printStream.println("WE MADE IT TO PublishSQStepExecution. Name is: " + step.getName());

        String orgName = Util.isNullOrEmpty(step.getOrgName()) ? envVars.get("IBM_CLOUD_DEVOPS_ORG") : step.getOrgName();
        String applicationName =  Util.isNullOrEmpty(step.getApplicationName()) ? envVars.get("IBM_CLOUD_DEVOPS_APP_NAME") : step.getApplicationName();
        String toolchainName = Util.isNullOrEmpty(step.getToolchainId()) ? envVars.get("IBM_CLOUD_DEVOPS_TOOLCHAIN_ID") : step.getToolchainId();
        String username = envVars.get("IBM_CLOUD_DEVOPS_CREDS_USR");
        String password = envVars.get("IBM_CLOUD_DEVOPS_CREDS_PSW");

        printStream.println(orgName);
        printStream.println(applicationName);
        printStream.println(toolchainName);
        printStream.println(username);
        printStream.println(password);


        //check all the required env vars
        if (!Util.allNotNullOrEmpty(orgName, applicationName,toolchainName, username, password)) {
            printStream.println("[IBM Cloud DevOps] Missing environment variables configurations, please specify all required environment variables in the pipeline");
            printStream.println("[IBM Cloud DevOps] Error: Failed to upload Test Result.");
            return null;
        }


        if (true) {
            PublishSQ publisher = new PublishSQ(
                    orgName,
                    applicationName,
                    toolchainName,
                    username,
                    password);
            publisher.perform(build, ws, launcher, listener);
        } else {
            printStream.println("[IBM Cloud DevOps] the \"result\" in the publishBuildRecord should be either \"PASS\" or \"FAIL\"");
        }

        return null;
    }
}
