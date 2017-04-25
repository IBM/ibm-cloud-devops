/*
    <notice>
    Copyright 2016, 2017 IBM Corporation
     Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
    The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
    </notice>
 */
package com.ibm.devops.notification;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.*;
import hudson.model.listeners.RunListener;
import net.sf.json.JSONObject;
import java.io.PrintStream;

/**
 * Created by patrickjoy on 2/3/17.
 */
@Extension
public class BuildListener extends RunListener<AbstractBuild> {

    public BuildListener(){
        super(AbstractBuild.class);
    }

    @Override
    public void onStarted(AbstractBuild r, TaskListener listener) {
        handleEvent(r, listener, "STARTED");
    }

    @Override
    public void onCompleted(AbstractBuild r, TaskListener listener) {
        handleEvent(r, listener, "COMPLETED");
    }

    @Override
    public void onFinalized(AbstractBuild r){
        handleEvent(r, TaskListener.NULL, "FINALIZED");
    }

    private void handleEvent(AbstractBuild r, TaskListener listener, String phase) {
        OTCNotifier notifier = EventHandler.findPublisher(r);
        PrintStream printStream = listener.getLogger();
        EnvVars envVars = EventHandler.getEnv(r, listener, printStream);
        String webhook = EventHandler.getWebhookFromEnv(envVars);
        Result result = r.getResult();
        Boolean isRelevant = EventHandler.isRelevant(notifier, phase, result);

        if(isRelevant) {
            String resultString = null;

            if(result != null){
                resultString = result.toString();
            }

            JSONObject message = MessageHandler.buildMessage(r, envVars, phase, resultString);
            MessageHandler.postToWebhook(webhook, message, printStream);
        }
    }
}