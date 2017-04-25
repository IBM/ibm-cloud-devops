/*
 <notice>

 Copyright 2017 IBM Corporation

 Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

 </notice>
 */

package com.ibm.devops.notification.steps;

import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import hudson.Extension;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;

/**
 * Created by patrickjoy on 3/30/17.
 */
public class OTCNotificationStep extends AbstractStepImpl {
    //required parameter to support pipeline script
    private String status;
    private String stageName;

    //option parameters
    private String webhookUrl;

    @DataBoundConstructor
    public OTCNotificationStep(String stageName, String status){
        this.stageName = stageName;
        this.status = status;
    }

    @DataBoundSetter
    public void setWebhookUrl(String webhookUrl){
        this.webhookUrl = webhookUrl;
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
