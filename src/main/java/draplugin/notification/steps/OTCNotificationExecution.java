package draplugin.notification.steps;

import javax.inject.Inject;

import draplugin.notification.BuildListener;
import draplugin.notification.MessageHandler;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.Result;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;

import java.io.PrintStream;

/**
 * Created by patrickjoy on 3/30/17.
 */
public class OTCNotificationExecution extends AbstractSynchronousNonBlockingStepExecution<Void> {
    @Inject
    private transient OTCNotificationStep step;

    @StepContextParameter
    private transient TaskListener listener;
    @StepContextParameter
    private transient Run build;
    @StepContextParameter
    private transient EnvVars envVars;

    @Override
    protected Void run() throws Exception {
        String webhookUrl = step.getWebhookUrl().trim();
        String stageName = step.getStageName().trim();
        String status = step.getStatus().toUpperCase().trim();

        PrintStream printStream = listener.getLogger();

        JSONObject message = MessageHandler.buildMessage(build, envVars, stageName, status);
        MessageHandler.postToWebhook(webhookUrl, message, printStream);

        return null;
    }
}
