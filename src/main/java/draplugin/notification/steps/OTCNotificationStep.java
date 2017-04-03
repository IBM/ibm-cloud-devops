package draplugin.notification.steps;

import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import hudson.Extension;
import org.kohsuke.stapler.DataBoundConstructor;
import javax.annotation.Nonnull;

/**
 * Created by patrickjoy on 3/30/17.
 */
public class OTCNotificationStep extends AbstractStepImpl {
    //required parameter to support pipeline script
    private String webhookUrl;
    private String status;
    private String stageName;

    @DataBoundConstructor
    public OTCNotificationStep(String webhookUrl, String stageName, String status){
        this.webhookUrl = webhookUrl;
        this.stageName = stageName;
        this.status = status;
    }

    public String getWebhookUrl(){
        return this.webhookUrl;
    }

    public String getStatus(){
        return this.status;
    }

    public String getStageName(){
        return this.stageName;
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {
        public DescriptorImpl() { super(OTCNotificationExecution.class); }

        @Override
        public String getFunctionName() {
            return "notifyOTC";
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Send notification to OTC";
        }
    }
}
