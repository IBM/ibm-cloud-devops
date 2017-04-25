package com.ibm.devops.dra.steps;

import hudson.Extension;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;

/**
 * Created by lix on 3/23/17.
 */
public class EvaluateGateStep extends AbstractStepImpl {
    // optional form fields from UI
    private String applicationName;
    private String orgName;
    private String credentialsId;
    private String toolchainId;

    // required parameters to support pipeline script
    private String policy;

    // optional gate parameters
    private String forceDecision;
    private String environment;

    @DataBoundConstructor
    public EvaluateGateStep(String policy) {
        this.policy = policy;
    }

    @DataBoundSetter
    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    @DataBoundSetter
    public void setOrgName(String orgName) {
        this.orgName = orgName;
    }

    @DataBoundSetter
    public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    @DataBoundSetter
    public void setToolchainId(String toolchainId) {
        this.toolchainId = toolchainId;
    }

    @DataBoundSetter
    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    @DataBoundSetter
    public void setForceDecision(String forceDecision) {
        this.forceDecision = forceDecision;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public String getOrgName() {
        return orgName;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public String getToolchainId() {
        return toolchainId;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getPolicy() {
        return policy;
    }

    public String getForceDecision() {
        return forceDecision;
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() { super(EvaluateGateStepExecution.class); }

        @Override
        public String getFunctionName() {
            return "evaluateGate";
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "IBM Cloud DevOps Gate";
        }
    }
}
