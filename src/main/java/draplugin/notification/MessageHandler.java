package draplugin.notification;

import hudson.EnvVars;
import hudson.model.Run;
import hudson.model.Job;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import java.io.IOException;
import java.io.PrintStream;
import draplugin.dra.Util;

/**
 * Created by patrickjoy on 4/3/17.
 */
//build message that will be posted to the webhook
public class MessageHandler {
    public static JSONObject buildMessage(Run r, EnvVars envVars, String phase, String result){
        JSONObject message = new JSONObject();
        JSONObject build = new JSONObject();
        JSONObject scm = new JSONObject();

        Job job = r.getParent();
        String fullUrl = Jenkins.getInstance().getRootUrl();

        //setup scm
        if(envVars != null) {
            String gitCommit = envVars.get("GIT_COMMIT");
            String gitBranch = envVars.get("GIT_BRANCH");
            String gitPreviousCommit = envVars.get("GIT_PREVIOUS_COMMIT");
            String gitPreviousSuccessfulCommit = envVars.get("GIT_PREVIOUS_SUCCESSFUL_COMMIT");
            String gitUrl = envVars.get("GIT_URL");
            String gitCommitterName = envVars.get("GIT_COMMITTER_NAME");
            String gitCommitterEmail = envVars.get("GIT_COMMITTER_EMAIL");
            String gitAuthorName = envVars.get("GIT_AUTHOR_NAME");
            String gitAuthorEmail = envVars.get("GIT_AUTHOR_EMAIL");

            if (gitCommit != null) {
                scm.put("git_commit", gitCommit);
            }

            if (gitBranch != null) {
                scm.put("git_branch", gitBranch);
            }

            if (gitPreviousCommit != null) {
                scm.put("git_previous_commit", gitPreviousCommit);
            }

            if (gitPreviousSuccessfulCommit != null) {
                scm.put("git_previous_successful_commit", gitPreviousSuccessfulCommit);
            }

            if (gitUrl != null) {
                scm.put("git_url", gitUrl);
            }

            if (gitCommitterName != null) {
                scm.put("git_committer_name", gitCommitterName);
            }

            if (gitCommitterEmail != null) {
                scm.put("git_committer_email", gitCommitterEmail);
            }

            if (gitAuthorName != null) {
                scm.put("git_author_name", gitAuthorName);
            }

            if (gitAuthorEmail != null) {
                scm.put("git_author_email", gitAuthorEmail);
            }
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
            build.put("status", result);
        }

        if(phase != "STARTED") {
            build.put("duration", r.getDuration());
        }

        build.put("scm", scm);

        //setup the message
        message.put("name", job.getName());
        message.put("url", job.getUrl());
        message.put("build", build);

        return message;
    }

    //post message to webhook
    public static void postToWebhook(String webhook, JSONObject message, PrintStream printStream){
        //check webhook
        if(Util.isNullOrEmpty(webhook)){
            printStream.println("[IBM Cloud DevOps] IBM_CLOUD_DEVOPS_WEBHOOK_URL not set.");
            printStream.println("[IBM Cloud DevOps] Error: Failed to notify OTC.");
        } else {
            CloseableHttpClient httpClient = HttpClients.createDefault();
            HttpPost postMethod = new HttpPost(webhook);
            try {
                StringEntity data = new StringEntity(message.toString());
                postMethod.setEntity(data);
                postMethod = Proxy.addProxyInformation(postMethod);
                postMethod.addHeader("Content-Type", "application/json");
                CloseableHttpResponse response = httpClient.execute(postMethod);

                if (response.getStatusLine().toString().matches(".*2([0-9]{2}).*")) {
                    printStream.println("[IBM Cloud DevOps] Message successfully posted to webhook.");
                } else {
                    printStream.println("[IBM Cloud DevOps] Message failed, response status: " + response.getStatusLine());
                }
            } catch (IOException e) {
                printStream.println("[IBM Cloud DevOps] IOException, could not post to webhook:");
                e.printStackTrace(printStream);
            }
        }
    }
}
