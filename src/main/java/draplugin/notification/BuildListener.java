/*
    <notice>
    Copyright (c)2016,2017 IBM Corporation
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
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

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

    private String setWebhookFromEnv(AbstractBuild<?, ?> r, TaskListener listener){
        this.printStream = listener.getLogger();
        String webhook = null;
        EnvVars envVars = null;
        try {
            envVars = r.getEnvironment(listener);
            webhook = envVars.get("ICD_WEBHOOK_URL");

            if(webhook != null){
                webhook = webhook.trim();
            }
        } catch (IOException e) {
            this.printStream.println("[IBM Cloud DevOps Plugin] Exception: ");
            e.printStackTrace(this.printStream);
        } catch (InterruptedException e) {
            this.printStream.println("[IBM Cloud DevOps Plugin] Exception: ");
            e.printStackTrace(this.printStream);
        }

        return webhook;
    }

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

    private void filterMessages(AbstractBuild r, TaskListener listener, OTCNotifier notifier, String phase){
        this.webhook = setWebhookFromEnv(r, listener);
        this.printStream = listener.getLogger();
        boolean onStarted;
        boolean onCompleted;
        boolean onFinalized;
        boolean failureOnly;
        Result result = r.getResult();

        if(notifier != null){
            onStarted = notifier.getOnStarted();
            onCompleted = notifier.getOnCompleted();
            onFinalized = notifier.getOnFinalized();
            failureOnly = notifier.getFailureOnly();

            if(this.webhook == null || this.webhook.isEmpty()){//check webhook
                this.printStream.println("[IBM Cloud DevOps Plugin] String Parameter ICD_WEBHOOK_URL not set.");
            } else if(onStarted && phase == "STARTED" || onCompleted && phase == "COMPLETED" || onFinalized && phase == "FINALIZED"){//check selections
                if(failureOnly && result == Result.FAILURE || !failureOnly){//check failureOnly
                    buildMessage(r, listener, notifier, phase, result);
                }
            }
        }
    }

    private void buildMessage(AbstractBuild r, TaskListener listener, OTCNotifier notifier, String phase, Result result){
        JSONObject message = new JSONObject();
        JSONObject build = new JSONObject();
        JSONObject scm = new JSONObject();

        Job job = r.getParent();
        String fullUrl = Jenkins.getInstance().getRootUrl();

        //setup scm
        try {
            EnvVars envVars = r.getEnvironment(listener);
            String gitCommit = envVars.get("GIT_COMMIT");
            String gitBranch = envVars.get("GIT_BRANCH");
            String gitPreviousCommit = envVars.get("GIT_PREVIOUS_COMMIT");
            String gitPreviousSuccessfulCommit = envVars.get("GIT_PREVIOUS_SUCCESSFUL_COMMIT");
            String gitUrl = envVars.get("GIT_URL");
            String gitCommitterName = envVars.get("GIT_COMMITTER_NAME");
            String gitCommitterEmail = envVars.get("GIT_COMMITTER_EMAIL");
            String gitAuthorName = envVars.get("GIT_AUTHOR_NAME");
            String gitAuthorEmail = envVars.get("GIT_AUTHOR_EMAIL");

            if(gitCommit != null){
                scm.put("git_commit", gitCommit);
            }

            if(gitBranch != null){
                scm.put("git_branch", gitBranch);
            }

            if(gitPreviousCommit != null){
                scm.put("git_previous_commit", gitPreviousCommit);
            }

            if(gitPreviousSuccessfulCommit != null){
                scm.put("git_previous_successful_commit", gitPreviousSuccessfulCommit);
            }

            if(gitUrl != null){
                scm.put("git_url", gitUrl);
            }

            if(gitCommitterName != null){
                scm.put("git_committer_name", gitCommitterName);
            }

            if(gitCommitterEmail != null){
                scm.put("git_committer_email", gitCommitterEmail);
            }

            if(gitAuthorName != null){
                scm.put("git_author_name", gitAuthorName);
            }

            if(gitAuthorEmail != null){
                scm.put("git_author_email", gitAuthorEmail);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        //setup the build object
        build.put("number", r.getNumber());
        build.put("queue_id", r.getQueueId());
        build.put("phase", phase);
        build.put("url", r.getUrl());

        if(fullUrl != null){
            build.put("full_url", fullUrl);
        } else{
            build.put("full_url", "");
        }

        if(result != null){
            build.put("status", result.toString());
        }

        if(phase != "STARTED") {
            build.put("duration", r.getDuration());
        }

        build.put("scm", scm);

        //setup the message
        message.put("name", job.getName());
        message.put("url", job.getUrl());
        message.put("build", build);

        postToWebhook(message, phase);
    }

    private void postToWebhook(JSONObject message, String phase){
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost postMethod = new HttpPost(this.webhook);
        try {
            StringEntity data = new StringEntity(message.toString());
            postMethod.setEntity(data);
            postMethod.addHeader("Content-Type", "application/json");
            CloseableHttpResponse response = httpClient.execute(postMethod);

            if (response.getStatusLine().toString().matches(".*2([0-9]{2}).*")) {
                this.printStream.println("[IBM Cloud DevOps Plugin] Message successfully posted to webhook.");
            } else {
                this.printStream.println("[IBM Cloud DevOps Plugin] Message failed, response status: " + response.getStatusLine());
            }
        } catch (IOException e) {
            this.printStream.println("[IBM Cloud DevOps Plugin] IOException, could not post to webhook:");
            e.printStackTrace(this.printStream);
        }
    }
}