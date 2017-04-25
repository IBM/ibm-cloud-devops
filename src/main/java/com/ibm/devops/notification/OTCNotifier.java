/*
 <notice>

 Copyright 2017 IBM Corporation

 Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

 </notice>
 */

package com.ibm.devops.notification;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import org.kohsuke.stapler.DataBoundConstructor;
import java.io.IOException;

/**
 * Created by Patrick Joy on 2/1/17.
 */
public class OTCNotifier extends Notifier {
    private boolean onStarted;
    private boolean onCompleted;
    private boolean onFinalized;
    private boolean failureOnly;

    /*
    The paramater names in @DataBoundConstructor need to match the fields in config.jelly exactly
     */
    @DataBoundConstructor
    public OTCNotifier(boolean onStarted,
                       boolean onCompleted,
                       boolean onFinalized,
                       boolean failureOnly
                       ){
        this.onStarted = onStarted;
        this.onCompleted = onCompleted;
        this.onFinalized = onFinalized;
        this.failureOnly = failureOnly;
    }

    /*
    These methods are called by jenkins to populate the per-job config fields
     */
    public Boolean getOnStarted(){
        return this.onStarted;
    }

    public Boolean getOnCompleted(){
        return this.onCompleted;
    }

    public Boolean getOnFinalized(){
        return this.onFinalized;
    }

    public Boolean getFailureOnly(){
        return this.failureOnly;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        return true;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    /*
    The descriptor allows global configs for your plugin, this class will be passed to every instance of the plugin.
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        @Override
        public String getDisplayName() {
            return "Notify OTC";//This is the plugin name in the config
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true; //It is always ok for someone to add this as a build step
        }
    }
}