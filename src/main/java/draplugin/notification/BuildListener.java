/*
    <notice>
    Copyright 2016, 2017 IBM Corporation
     Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
    The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
    </notice>
 */
package draplugin.notification;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.*;
import hudson.model.listeners.RunListener;
import hudson.tasks.Publisher;
import net.sf.json.JSONObject;
import draplugin.dra.Util;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

/**
 * Created by patrickjoy on 2/3/17.
 */
@Extension
public class BuildListener extends RunListener<AbstractBuild> {
    private String webhook = null;
    private PrintStream printStream = TaskListener.NULL.getLogger();

    public BuildListener(){
        super(AbstractBuild.class);
    }

    @Override
    public void onStarted(AbstractBuild r, TaskListener listener) {
        OTCNotifier notifier = findPublisher(r);
        filterMessages(r, listener, notifier, "STARTED");
    }

    @Override
    public void onCompleted(AbstractBuild r, TaskListener listener){
        OTCNotifier notifier = findPublisher(r);
        filterMessages(r, listener, notifier, "COMPLETED");
    }

    @Override
    public void onFinalized(AbstractBuild r){
        OTCNotifier notifier = findPublisher(r);
        filterMessages(r, TaskListener.NULL, notifier, "FINALIZED");
    }

    //filter messages based on user selection on the gui
    private void filterMessages(AbstractBuild r, TaskListener listener, OTCNotifier notifier, String phase){
        EnvVars envVars = getEnv(r, listener);
        this.webhook = setWebhookFromEnv(r, listener, envVars);
        this.printStream = listener.getLogger();
        boolean onStarted;
        boolean onCompleted;
        boolean onFinalized;
        boolean failureOnly;
        Result result = r.getResult();

        //Make sure OTC Notifier was found in the publisherList
        if(notifier != null){
            onStarted = notifier.getOnStarted();
            onCompleted = notifier.getOnCompleted();
            onFinalized = notifier.getOnFinalized();
            failureOnly = notifier.getFailureOnly();

            if(onStarted && phase == "STARTED" || onCompleted && phase == "COMPLETED" || onFinalized && phase == "FINALIZED"){//check selections
                if(failureOnly && result == Result.FAILURE || !failureOnly){//check failureOnly
                    String resultString = null;

                    if(result != null){
                        resultString = result.toString();
                    }

                    JSONObject message = MessageHandler.buildMessage(r, envVars, phase, resultString);
                    MessageHandler.postToWebhook(this.webhook, message, this.printStream);
                }
            }
        }
    }

    //search through the list of publishers to find and return OTCNotifier,
    private OTCNotifier findPublisher(AbstractBuild r){
        List<Publisher> publisherList = r.getProject().getPublishersList().toList();

        //ensure that there is an OTCNotifier in the project
        for(Publisher publisher: publisherList){
            if(publisher instanceof OTCNotifier){
                return (OTCNotifier) publisher;
            }
        }

        return null;
    }

    //get the build env
    private EnvVars getEnv(AbstractBuild r, TaskListener listener){
        try {
            return r.getEnvironment(listener);
        } catch (IOException e) {
            this.printStream.println("[IBM Cloud DevOps] Exception: ");
            this.printStream.println("[IBM Cloud DevOps] Error: Failed to notify OTC.");
            e.printStackTrace(this.printStream);
        } catch (InterruptedException e) {
            this.printStream.println("[IBM Cloud DevOps] Exception: ");
            this.printStream.println("[IBM Cloud DevOps] Error: Failed to notify OTC.");
            e.printStackTrace(this.printStream);
        }

        return null;
    }

    //set the webhook from the build env
    private String setWebhookFromEnv(AbstractBuild<?, ?> r, TaskListener listener, EnvVars envVars){
        this.printStream = listener.getLogger();
        String webhook = null;

        if(envVars != null) {
            webhook = envVars.get("IBM_CLOUD_DEVOPS_WEBHOOK_URL");

            //backward compatability
            if (Util.isNullOrEmpty(webhook)) {
                webhook = envVars.get("ICD_WEBHOOK_URL");
            }
        }

        return webhook;
    }
}