/**
 <notice>

 Copyright (c)2016 IBM Corporation

 Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

 </notice>
 */

package draplugin.dra;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import hudson.EnvVars;
import hudson.ProxyConfiguration;
import hudson.model.*;
import hudson.security.ACL;
import hudson.tasks.Recorder;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.cloudfoundry.client.lib.CloudCredentials;
import org.cloudfoundry.client.lib.CloudFoundryClient;
import org.cloudfoundry.client.lib.CloudFoundryException;
import org.cloudfoundry.client.lib.HttpProxyConfiguration;

import java.io.IOException;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.Scanner;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 *
 * Abstract DRA Builder to share common method between two different post-build actions
 * Created by Xunrong Li on 6/15/16.
 */
public abstract class AbstractDevOpsAction extends Recorder {

    public final static Logger LOGGER = Logger.getLogger(AbstractDevOpsAction.class.getName());

    private static Map<String, String> TARGET_API_MAP = ImmutableMap.of(
            "production", "https://api.ng.bluemix.net",
            "dev", "https://api.stage1.ng.bluemix.net",
            "new", "https://api.stage1.ng.bluemix.net",
            "stage1", "https://api.stage1.ng.bluemix.net"
    );

    private static Map<String, String> ORGANIZATIONS_URL_MAP = ImmutableMap.of(
            "production", "https://api.ng.bluemix.net/v2/organizations?q=name:",
            "dev", "https://api.stage1.ng.bluemix.net/v2/organizations?q=name:",
            "new", "https://api.stage1.ng.bluemix.net/v2/organizations?q=name:",
            "stage1", "https://api.stage1.ng.bluemix.net/v2/organizations?q=name:"
    );

    private static Map<String, String> TOOLCHAINS_URL_MAP = ImmutableMap.of(
            "production", "https://otc-api.ng.bluemix.net/api/v1/toolchains?organization_guid=",
            "dev", "https://otc-api.stage1.ng.bluemix.net/api/v1/toolchains?organization_guid=",
            "new", "https://otc-api-integration.stage1.ng.bluemix.net/api/v1/toolchains?organization_guid=",
            "stage1", "https://otc-api.stage1.ng.bluemix.net/api/v1/toolchains?organization_guid="
    );

    private static Map<String, String> POLICIES_URL_MAP = ImmutableMap.of(
            "production", "https://dra.ng.bluemix.net/api/v4/organizations/{org_name}/toolchainids/{toolchain_name}/policies",
            "dev", "https://dev-dra.stage1.ng.bluemix.net/api/v4/organizations/{org_name}/toolchainids/{toolchain_name}/policies",
            "new", "https://new-dra.stage1.ng.bluemix.net/api/v4/organizations/{org_name}/toolchainids/{toolchain_name}/policies",
            "stage1", "https://dra.stage1.ng.bluemix.net/api/v4/organizations/{org_name}/toolchainids/{toolchain_name}/policies"
    );

    private static Map<String, String> DLMS_ENV_MAP = ImmutableMap.of(
            "production", "https://dlms.ng.bluemix.net/v2",
            "dev", "https://dev-dlms.stage1.ng.bluemix.net/v2",
            "new", "https://new-dlms.stage1.ng.bluemix.net/v2",
            "stage1", "https://dlms.stage1.ng.bluemix.net/v2"
    );

    private static Map<String, String> GATE_DECISION_ENV_MAP = ImmutableMap.of(
            "production", "https://dra.ng.bluemix.net/api/v4",
            "dev", "https://dev-dra.stage1.ng.bluemix.net/api/v4",
            "new", "https://new-dra.stage1.ng.bluemix.net/api/v4",
            "stage1", "https://dra.stage1.ng.bluemix.net/api/v4"
    );

    private static Map<String, String> CONTROL_CENTER_ENV_MAP = ImmutableMap.of(
            "production", "https://console.ng.bluemix.net/devops/insights/#!/",
            "dev", "https://dev-console.stage1.ng.bluemix.net/devops/insights/#!/",
            "new", "https://new-console.stage1.ng.bluemix.net/devops/insights/#!/",
            "stage1", "https://console.stage1.ng.bluemix.net/devops/insights/#!/"
    );

    private static Map<String, String> DRA_REPORT_ENV_MAP = ImmutableMap.of(
            "production", "https://dra.ng.bluemix.net/report/",
            "dev", "https://dev-dra.stage1.ng.bluemix.net/report/",
            "new", "https://new-dra.stage1.ng.bluemix.net/report/",
            "stage1", "https://dra.stage1.ng.bluemix.net/report/"
    );

    public static void printPluginVersion(ClassLoader loader, PrintStream printStream) {
        final Properties properties = new Properties();
        try {
            properties.load(loader.getResourceAsStream("plugin.properties"));
            if (properties != null) {
                printStream.println("[DevOps Insight Plugin] version: " + properties.getProperty("version"));
            } else {
                printStream.println("[DevOps Insight Plugin] failed to get version");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }



    public static String chooseTargetAPI(String environment) {
        if (!Util.isNullOrEmpty(environment)) {
            String target_api = TARGET_API_MAP.get(environment);
            if (!Util.isNullOrEmpty(target_api)) {
                return target_api;
            }
        }

        return TARGET_API_MAP.get("production");
    }

    public static String chooseToolchainsUrl(String environment) {
        if (!Util.isNullOrEmpty(environment)) {
            String toolchains_url = TOOLCHAINS_URL_MAP.get(environment);
            if (!Util.isNullOrEmpty(toolchains_url)) {
                return toolchains_url;
            }
        }

        return TOOLCHAINS_URL_MAP.get("production");
    }

    public static String chooseOrganizationsUrl(String environment) {
        if (!Util.isNullOrEmpty(environment)) {
            String organizations_url = ORGANIZATIONS_URL_MAP.get(environment);
            if (!Util.isNullOrEmpty(organizations_url)) {
                return organizations_url;
            }
        }

        return ORGANIZATIONS_URL_MAP.get("production");
    }

    public static String choosePoliciesUrl(String environment) {
        if (!Util.isNullOrEmpty(environment)) {
            String policies_url = POLICIES_URL_MAP.get(environment);
            if (!Util.isNullOrEmpty(policies_url)) {
                return policies_url;
            }
        }

        return POLICIES_URL_MAP.get("production");
    }


    /**
     * choose DLMS Url for different environment (production, stage1, new, dev)
     * @param envStr
     * @return
     */

    public static String chooseDLMSUrl(String envStr) {
        if (!Util.isNullOrEmpty(envStr)) {
            String dlmsUrl = DLMS_ENV_MAP.get(envStr);
            if (!Util.isNullOrEmpty(dlmsUrl)) {
                return dlmsUrl;
            }
        }

        return DLMS_ENV_MAP.get("production");
    }

    /**
     * choose DRA Url for different environment (production, stage1, new, dev)
     * @param envStr
     * @return
     */
    public static String chooseDRAUrl(String envStr) {
        if (!Util.isNullOrEmpty(envStr)) {
            String draUrl = GATE_DECISION_ENV_MAP.get(envStr);
            if (!Util.isNullOrEmpty(draUrl)) {
                return draUrl;
            }
        }

        return GATE_DECISION_ENV_MAP.get("production");
    }

    /**
     * choose control center Url for different environment (production, stage1, new, dev)
     * @param envStr
     * @return
     */
    public static String chooseControlCenterUrl(String envStr) {
        if (!Util.isNullOrEmpty(envStr)) {
            String ccUrl = CONTROL_CENTER_ENV_MAP.get(envStr);
            if (!Util.isNullOrEmpty(ccUrl)) {
                return ccUrl;
            }
        }
        return CONTROL_CENTER_ENV_MAP.get("production");
    }

    /**
     * choose report prefix url for different environment (production, stage1, new, dev)
     * @param envStr
     * @return
     */
    public static String chooseReportUrl(String envStr) {
        if (!Util.isNullOrEmpty(envStr)) {
            String ccUrl = DRA_REPORT_ENV_MAP.get(envStr);
            if (!Util.isNullOrEmpty(ccUrl)) {
                return ccUrl;
            }
        }
        return DRA_REPORT_ENV_MAP.get("production");
    }

    /**
     * Get the Bluemix Token using Cloud Foundry as the authentication with DLMS and DRA backend
     * @param context - the current job
     * @param credentialsId - the credential id in Jenkins
     * @param targetAPI - the target api that used for logging in to the Bluemix
     * @return the bearer token
     */
    public static String GetBluemixToken(Job context, String credentialsId, String targetAPI) throws MalformedURLException, CloudFoundryException {

        try {
            List<StandardUsernamePasswordCredentials> standardCredentials = CredentialsProvider.lookupCredentials(
                    StandardUsernamePasswordCredentials.class,
                    context,
                    ACL.SYSTEM,
                    URIRequirementBuilder.fromUri(targetAPI).build());

            StandardUsernamePasswordCredentials credentials =
                    CredentialsMatchers.firstOrNull(standardCredentials, CredentialsMatchers.withId(credentialsId));


            CloudCredentials cloudCredentials = new CloudCredentials(credentials.getUsername(), Secret.toString(credentials.getPassword()));


            URL url = new URL(targetAPI);
            HttpProxyConfiguration configuration = buildProxyConfiguration(url);

            CloudFoundryClient client = new CloudFoundryClient(cloudCredentials, url, configuration, true);
            return "bearer " + client.login().toString();

        } catch (MalformedURLException e) {
            throw e;
        } catch (CloudFoundryException e) {
            throw e;
        }
    }

    public static String GetBluemixToken(ItemGroup context, String credentialsId, String targetAPI) throws MalformedURLException, CloudFoundryException {

        try {
            List<StandardUsernamePasswordCredentials> standardCredentials = CredentialsProvider.lookupCredentials(
                    StandardUsernamePasswordCredentials.class,
                    context,
                    ACL.SYSTEM,
                    URIRequirementBuilder.fromUri(targetAPI).build());

            StandardUsernamePasswordCredentials credentials =
                    CredentialsMatchers.firstOrNull(standardCredentials, CredentialsMatchers.withId(credentialsId));

            CloudCredentials cloudCredentials = new CloudCredentials(credentials.getUsername(), Secret.toString(credentials.getPassword()));


            URL url = new URL(targetAPI);
            HttpProxyConfiguration configuration = buildProxyConfiguration(url);

            CloudFoundryClient client = new CloudFoundryClient(cloudCredentials, url, configuration, true);
            return "bearer " + client.login().toString();

        } catch (MalformedURLException e) {
            throw e;
        } catch (CloudFoundryException e) {
            throw e;
        }
    }


    /**
     * build proxy for cloud foundry http connection
     * @param targetURL - target API URL
     * @return the full target URL
     */
    private static HttpProxyConfiguration buildProxyConfiguration(URL targetURL) {
        ProxyConfiguration proxyConfig = Jenkins.getInstance().proxy;
        if (proxyConfig == null) {
            return null;
        }

        String host = targetURL.getHost();
        for (Pattern p : proxyConfig.getNoProxyHostPatterns()) {
            if (p.matcher(host).matches()) {
                return null;
            }
        }

        return new HttpProxyConfiguration(proxyConfig.name, proxyConfig.port);
    }

    /**
     * get the root project
     * @param job - the source job
     * @return the root project
     */
    private static Job<?, ?> getRootProject(Job<?, ?> job) {
        if (job instanceof AbstractProject) {
            return ((AbstractProject<?,?>)job).getRootProject();
        } else {
            return job;
        }
    }

    // retrieve the "folder" (jenkins root if no folder used) for this build
    private static ItemGroup getItemGroup(Run<?, ?> build) {
        return getRootProject(build.getParent()).getParent();
    }


    /**
     * Recursive function to locate the triggered build
     * @param job - the target job
     * @param parent - the current job
     * @return the specific build of the target job
     */
    private static Run<?,?> getBuild(Job<?,?> job, Run<?,?> parent) {
        Run<?,?> result = null;

        // Upstream job for matrix will be parent project, not only individual configuration:
        List<String> jobNames = new ArrayList<>();
        jobNames.add(job.getFullName());
        if ((job instanceof AbstractProject<?,?>) && ((AbstractProject<?,?>)job).getRootProject() != job) {
            jobNames.add(((AbstractProject<?,?>)job).getRootProject().getFullName());
        }

        List<Run<?, ?>> upstreamBuilds = new ArrayList<>();

        for (Cause cause: parent.getCauses()) {
            if (cause instanceof Cause.UpstreamCause) {
                Cause.UpstreamCause upstream = (Cause.UpstreamCause) cause;
                Run<?, ?> upstreamRun = upstream.getUpstreamRun();
                if (upstreamRun != null) {
                    upstreamBuilds.add(upstreamRun);
                }
            }
        }

        if (parent instanceof AbstractBuild) {
            AbstractBuild<?, ?> parentBuild = (AbstractBuild<?,?>)parent;

            Map<AbstractProject, Integer> parentUpstreamBuilds = parentBuild.getUpstreamBuilds();
            for (Map.Entry<AbstractProject, Integer> buildEntry : parentUpstreamBuilds.entrySet()) {
                upstreamBuilds.add(buildEntry.getKey().getBuildByNumber(buildEntry.getValue()));
            }
        }

        for (Run<?, ?> upstreamBuild : upstreamBuilds) {
            Run<?,?> run;

            if(upstreamBuild == null) {
            	continue;
            }
            if (jobNames.contains(upstreamBuild.getParent().getFullName())) {
                // Use the 'job' parameter instead of directly the 'upstreamBuild', because of Matrix jobs.
                run = job.getBuildByNumber(upstreamBuild.getNumber());
            } else {
                // Figure out the parent job and do a recursive call to getBuild
                run = getBuild(job, upstreamBuild);
            }

            if (run != null){
                if ((result == null) || (result.getNumber() > run.getNumber())) {
                    result = run;
                }
            }

        }

        return result;
    }

    /**
     * locate triggered build
     * @param build - the current running build of this job
     * @param name - the build job name that you are going to locate
     * @param printStream - logger
     * @return
     */
    public static Run<?,?> getTriggeredBuild(Run build, String name, EnvVars envVars, PrintStream printStream) {
        // if user specify the build job as current job or leave it empty
        if (name == null || name.isEmpty() || name.equals(build.getParent().getName())) {
            printStream.println("[DevOps Insight Plugin] Current job is the build job");
            return build;
        } else {
            name = envVars.expand(name);
            Job<?, ?> job = Jenkins.getInstance().getItem(name, getItemGroup(build), Job.class);
            if (job != null) {
                Run src = getBuild(job, build);
                if (src == null) {
                    // if user runs the test job independently
                    printStream.println("[DevOps Insight Plugin] Are you running the test job independently? Use the last successful build of the build job");
                    src = job.getLastSuccessfulBuild();
                }

                return src;
            } else {
                // if user does not specify the build job or can not find the build job that user specifies
                printStream.println("[DevOps Insight Plugin] ERROR: Failed to find the build job, please check the build job name");
                return null;
            }
        }
    }

    /**
     * Get the build number
     * @param build
     * @return
     */
    public String getBuildNumber(String jobName, Run build) {

        String jName = new String();
        Scanner s = new Scanner(jobName).useDelimiter("/");
        while(s.hasNext()){ // this will loop through the string until the last string(job name) is reached.
            jName = s.next();
        }
        s.close();

        String buildNumber = jName + ":" + build.getNumber();
        return buildNumber;
    }

    /**
     * Get a list of toolchains using given token and organization name.
     * @param token
     * @param orgName
     * @return
     */
    public static ListBoxModel getToolchainList(String token, String orgName, String environment, Boolean debug_mode) {

        LOGGER.setLevel(Level.INFO);

        if(debug_mode){
            LOGGER.info("#######################");
            LOGGER.info("TOKEN:" + token);
            LOGGER.info("ORG:" + orgName);
            LOGGER.info("ENVIRONMENT:" + environment);
        }

        String orgId = getOrgId(token,orgName, environment, debug_mode);
        ListBoxModel emptybox = new ListBoxModel();
        emptybox.add("","empty");

        if(orgId == null) {
            return emptybox;
        }

        CloseableHttpClient httpClient = HttpClients.createDefault();
        String toolchains_url = chooseToolchainsUrl(environment);
        if(debug_mode){
            LOGGER.info("GET TOOLCHAIN LIST URL:" + toolchains_url + orgId);
        }

        HttpGet httpGet = new HttpGet(toolchains_url + orgId);

        httpGet.setHeader("Authorization", token);
        CloseableHttpResponse response = null;

        try {
            response = httpClient.execute(httpGet);
            String resStr = EntityUtils.toString(response.getEntity());

            if(debug_mode){
                LOGGER.info("RESPONSE FROM TOOLCHAINS API:" + response.getStatusLine().toString());
            }
            if (response.getStatusLine().toString().contains("200")) {
                // get 200 response
                JsonParser parser = new JsonParser();
                JsonElement element = parser.parse(resStr);
                JsonObject obj = element.getAsJsonObject();
                JsonArray items = obj.getAsJsonArray("items");
                ListBoxModel toolchainList = new ListBoxModel();

                for (int i = 0; i < items.size(); i++) {
                    JsonObject toolchainObj = items.get(i).getAsJsonObject();
                    String toolchainName = String.valueOf(toolchainObj.get("name")).replaceAll("\"", "");
                    String toolchainID = String.valueOf(toolchainObj.get("toolchain_guid")).replaceAll("\"", "");
                    toolchainList.add(toolchainName,toolchainID);
                }
                if(debug_mode){
                    LOGGER.info("TOOLCHAIN LIST:" + toolchainList);
                    LOGGER.info("#######################");
                }
                if(toolchainList.isEmpty()) {
                    return emptybox;
                }
                return toolchainList;
            } else {
                return emptybox;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return emptybox;
    }

    public static String getOrgId(String token, String orgName, String environment, Boolean debug_mode) {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        String organizations_url = chooseOrganizationsUrl(environment);
        if(debug_mode){
            LOGGER.info("GET ORG_GUID URL:" + organizations_url + orgName);
        }
        HttpGet httpGet = new HttpGet(organizations_url + orgName);

        httpGet.setHeader("Authorization", token);
        CloseableHttpResponse response = null;

        try {
            response = httpClient.execute(httpGet);
            String resStr = EntityUtils.toString(response.getEntity());

            if(debug_mode){
                LOGGER.info("RESPONSE FROM ORGANIZATIONS API:" + response.getStatusLine().toString());
            }
            if (response.getStatusLine().toString().contains("200")) {
                // get 200 response
                JsonParser parser = new JsonParser();
                JsonElement element = parser.parse(resStr);
                JsonObject obj = element.getAsJsonObject();
                JsonArray resources = obj.getAsJsonArray("resources");

                if(resources.size() > 0) {
                    JsonObject resource = resources.get(0).getAsJsonObject();
                    JsonObject metadata = resource.getAsJsonObject("metadata");
                    if(debug_mode){
                        LOGGER.info("ORG_ID:" + String.valueOf(metadata.get("guid")).replaceAll("\"", ""));
                    }
                    return String.valueOf(metadata.get("guid")).replaceAll("\"", "");
                }
                else {
                    if(debug_mode){
                        LOGGER.info("RETURNED NO ORGANIZATIONS.");
                    }
                    return null;
                }

            } else {
                if(debug_mode){
                    LOGGER.info("RETURNED STATUS CODE OTHER THAN 200.");
                }
                return null;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Get a list of policies that belong to an org
     * @param token
     * @param orgName
     * @return
     */

    public static ListBoxModel getPolicyList(String token, String orgName, String toolchainName, String environment, Boolean debug_mode) {

        // get all jenkins job
        ListBoxModel emptybox = new ListBoxModel();
        emptybox.add("","empty");

        String url = choosePoliciesUrl(environment);
        url = url.replace("{org_name}", orgName);
        url = url.replace("{toolchain_name}", toolchainName);

        if(debug_mode){
            LOGGER.info("GET POLICIES URL:" + url);
        }

        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(url);

        httpGet.setHeader("Authorization", token);
        CloseableHttpResponse response = null;
        try {
            response = httpClient.execute(httpGet);
            String resStr = EntityUtils.toString(response.getEntity());

            if(debug_mode){
                LOGGER.info("RESPONSE FROM GET POLICIES URL:" + response.getStatusLine().toString());
            }
            if (response.getStatusLine().toString().contains("200")) {
                // get 200 response
                JsonParser parser = new JsonParser();
                JsonElement element = parser.parse(resStr);
                JsonArray jsonArray = element.getAsJsonArray();

                ListBoxModel model = new ListBoxModel();

                //model.add("select", "select"); why is this here? Need to ask.
                for (int i = 0; i < jsonArray.size(); i++) {
                    JsonObject obj = jsonArray.get(i).getAsJsonObject();
                    String name = String.valueOf(obj.get("name")).replaceAll("\"", "");
                    model.add(name, name);
                }
                if(debug_mode){
                    LOGGER.info("POLICY LIST:" + model);
                    LOGGER.info("#######################");
                }
                return model;
            } else {
                if(debug_mode){
                    LOGGER.info("RETURNED STATUS CODE OTHER THAN 200.");
                }
                return emptybox;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return emptybox;
    }

    /**
     * write to the environment variables to pass to next build step
     * @param build - the current build
     * @param bluemixToken - the Bluemix Token
     * @param buildId - the build number of the build job in the Jenkins
     */
    public static void passEnvToNextBuildStep (Run build, final String bluemixToken, final String buildId) {

        build.addAction(new EnvironmentContributingAction() {
                            @Override
                            public String getIconFileName() {
                                return null;
                            }

                            @Override
                            public String getDisplayName() {
                                return null;
                            }

                            @Override
                            public String getUrlName() {
                                return null;
                            }

                            public void buildEnvVars(AbstractBuild<?, ?> build, EnvVars envVars) {
                                if (envVars != null) {
                                    if (!Util.isNullOrEmpty(bluemixToken)) {
                                        envVars.put("DI_BM_TOKEN", bluemixToken);
                                    }

                                    if (!Util.isNullOrEmpty(buildId)) {
                                        envVars.put("DI_BUILD_ID", buildId);
                                    }
                                }
                            }
                        }
        );
    }

}
