/*
 <notice>

 Copyright 2016, 2017 IBM Corporation

 Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

 </notice>
 */

package com.ibm.devops.dra;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ibm.devops.dra.steps.AbstractDevOpsStep;
import hudson.EnvVars;
import hudson.model.*;
import hudson.tasks.Recorder;
import hudson.util.ListBoxModel;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.cloudfoundry.client.lib.CloudCredentials;
import org.cloudfoundry.client.lib.CloudFoundryClient;
import org.cloudfoundry.client.lib.CloudFoundryException;
import org.cloudfoundry.client.lib.HttpProxyConfiguration;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;
import java.util.logging.Logger;

import static com.ibm.devops.dra.UIMessages.*;
import static com.ibm.devops.dra.Util.*;

/**
 * Abstract DRA Builder to share common method between two different post-build actions
 */
public abstract class AbstractDevOpsAction extends Recorder {
    public final static Logger LOGGER = Logger.getLogger(AbstractDevOpsAction.class.getName());
    public final static String APP_NAME = "IBM_CLOUD_DEVOPS_APP_NAME";
    public final static String TOOLCHAIN_ID = "IBM_CLOUD_DEVOPS_TOOLCHAIN_ID";
    public final static String USERNAME = "IBM_CLOUD_DEVOPS_CREDS_USR";
    public final static String PASSWORD = "IBM_CLOUD_DEVOPS_CREDS_PSW";
    public final static String API_KEY = "IBM_CLOUD_DEVOPS_API_KEY";
    public final static String RESULT_SUCCESS = "SUCCESS";
    public final static String RESULT_FAIL = "FAIL";
    private final static String CONTENT_TYPE_JSON = "application/json";
    private final static String ORG = "&&organization_guid:";
    private final static String SPACE = "&&space_guid:";
    private final static String IAM_GRANT_TYPE = "urn:ibm:params:oauth:grant-type:apikey";
    private final static String IAM_RESPONSE_TYPE = "cloud_iam";
    private static final String REPORT_URL_PART = "decisionreport?toolchainId=";


    private static Map<String, String> TARGET_API_MAP = ImmutableMap.of(
            "production", "https://api.ng.bluemix.net",
            "dev", "https://api.stage1.ng.bluemix.net",
            "stage1", "https://api.stage1.ng.bluemix.net"
    );

    private static Map<String, String> IAM_API_MAP = ImmutableMap.of(
            "production", "https://iam.bluemix.net/identity/token?",
            "dev", "https:/iam.stage1.bluemix.net/identity/token?",
            "stage1", "https://iam.stage1.bluemix.net/identity/token?"
    );

    private static Map<String, String> ORGANIZATIONS_URL_MAP = ImmutableMap.of(
            "production", "https://api.ng.bluemix.net/v2/organizations?q=name:",
            "dev", "https://api.stage1.ng.bluemix.net/v2/organizations?q=name:",
            "stage1", "https://api.stage1.ng.bluemix.net/v2/organizations?q=name:"
    );

    private static Map<String, String> SPACES_URL_MAP = ImmutableMap.of(
            "production", "https://api.ng.bluemix.net/v2/spaces?q=name:",
            "dev", "https://api.stage1.ng.bluemix.net/v2/spaces?q=name:",
            "stage1", "https://api.stage1.ng.bluemix.net/v2/spaces?q=name:"
    );

    private static Map<String, String> APPS_URL_MAP = ImmutableMap.of(
            "production", "https://api.ng.bluemix.net/v2/apps?q=name:",
            "dev", "https://api.stage1.ng.bluemix.net/v2/apps?q=name:",
            "stage1", "https://api.stage1.ng.bluemix.net/v2/apps?q=name:"
    );

    private static Map<String, String> POLICIES_URL_MAP = ImmutableMap.of(
            "production", "https://dra.ng.bluemix.net/api/v5/toolchainids/{toolchain_name}/policies",
            "dev", "https://dev-dra.stage1.ng.bluemix.net/api/v5/toolchainids/{toolchain_name}/policies",
            "stage1", "https://dra.stage1.ng.bluemix.net/api/v5/toolchainids/{toolchain_name}/policies"
    );

    private static Map<String, String> DLMS_ENV_MAP = ImmutableMap.of(
            "production", "https://dlms.ng.bluemix.net/v3",
            "dev", "https://dev-dlms.stage1.ng.bluemix.net/v3",
            "stage1", "https://dlms.stage1.ng.bluemix.net/v3"
    );

    private static Map<String, String> GATE_DECISION_ENV_MAP = ImmutableMap.of(
            "production", "https://dra.ng.bluemix.net/api/v5",
            "dev", "https://dev-dra.stage1.ng.bluemix.net/api/v5",
            "stage1", "https://dra.stage1.ng.bluemix.net/api/v5"
    );

    private static Map<String, String> CONTROL_CENTER_ENV_MAP = ImmutableMap.of(
            "production", "https://console.ng.bluemix.net/devops/insights?env_id=ibm:yp:us-south#!/",
            "dev", "https://dev-console.stage1.ng.bluemix.net/devops/insights/#!/",
            "stage1", "https://console.stage1.ng.bluemix.net/devops/insights/#!/"
    );



    /**
     * get the environment based on the console
     * @param consoleUrl
     */
    public static String getEnv(String consoleUrl) {
        if (isNullOrEmpty(consoleUrl)) {
            return "production";
        } else if (consoleUrl.contains("dev-console.stage1.bluemix.net") || consoleUrl.contains("dev-console.stage1.ng.bluemix.net")) {
            return "dev";
        } else if (consoleUrl.contains("console.stage1.bluemix.net") || consoleUrl.contains("console.stage1.ng.bluemix.net")) {
            return "stage1";
        } else if (consoleUrl.contains("console.bluemix.net") || consoleUrl.contains("console.ng.bluemix.net")){
            return "production";
        } else {
            int start = consoleUrl.indexOf("console") + 8;
            int end = consoleUrl.indexOf("bluemix.net") - 1;
            String local = consoleUrl.substring(start, end);
            return local;
        }
    }

    /**
     * set the required env variables' HashMap for all steps
     * @param step
     * @param envVars
     * @return
     */
    public static HashMap<String, String> setRequiredEnvVars(AbstractDevOpsStep step, EnvVars envVars) {
        HashMap<String, String> requiredEnvVars = new HashMap<>();
        requiredEnvVars.put(APP_NAME, isNullOrEmpty(step.getApplicationName()) ? envVars.get(APP_NAME) : step.getApplicationName());
        requiredEnvVars.put(TOOLCHAIN_ID, isNullOrEmpty(step.getToolchainId()) ? envVars.get(TOOLCHAIN_ID) : step.getToolchainId());

        if (isNullOrEmpty(envVars.get(API_KEY))) {
            requiredEnvVars.put(USERNAME, envVars.get(USERNAME));
            requiredEnvVars.put(PASSWORD, envVars.get(PASSWORD));
        } else {
            requiredEnvVars.put(API_KEY, envVars.get(API_KEY).trim());
        }
        return requiredEnvVars;
    }

    public static String chooseTargetAPI(String environment) {
        if (!isNullOrEmpty(environment)) {
            if (TARGET_API_MAP.keySet().contains(environment)) {
                return TARGET_API_MAP.get(environment);
            } else {
                String api = TARGET_API_MAP.get("production").replace("ng", environment);
                return api;
            }
        }
        return TARGET_API_MAP.get("production");
    }

    public static String chooseIAMAPI(String environment) {
        if (!isNullOrEmpty(environment)) {
            if (IAM_API_MAP.keySet().contains(environment)) {
                return IAM_API_MAP.get(environment);
            } else {
                String api = IAM_API_MAP.get(environment).replace("ng", environment);
                return api;
            }
        }
        return IAM_API_MAP.get("production");
    }

    public static String chooseOrganizationsUrl(String environment) {
        if (!isNullOrEmpty(environment)) {
            if (ORGANIZATIONS_URL_MAP.keySet().contains(environment)) {
                return ORGANIZATIONS_URL_MAP.get(environment);
            } else {
                String api = ORGANIZATIONS_URL_MAP.get("production").replace("ng", environment);
                return api;
            }
        }
        return ORGANIZATIONS_URL_MAP.get("production");
    }

    public static String chooseSpacesUrl(String environment) {
        if (!isNullOrEmpty(environment)) {
            if (SPACES_URL_MAP.keySet().contains(environment)) {
                return SPACES_URL_MAP.get(environment);
            } else {
                String api = SPACES_URL_MAP.get("production").replace("ng", environment);
                return api;
            }
        }

        return SPACES_URL_MAP.get("production");
    }

    public static String chooseAppsUrl(String environment) {
        if (!isNullOrEmpty(environment)) {
            if (APPS_URL_MAP.keySet().contains(environment)) {
                return APPS_URL_MAP.get(environment);
            } else {
                String api = APPS_URL_MAP.get("production").replace("ng", environment);
                return api;
            }
        }
        return APPS_URL_MAP.get("production");
    }

    public static String choosePoliciesUrl(String environment) {
        if (!isNullOrEmpty(environment)) {
            if (POLICIES_URL_MAP.keySet().contains(environment)) {
                return POLICIES_URL_MAP.get(environment);
            } else {
                String api = POLICIES_URL_MAP.get("production").replace("ng", environment);
                return api;
            }
        }
        return POLICIES_URL_MAP.get("production");
    }

    /**
     * choose DLMS Url for different environment (production, stage1, new, dev)
     * @param environment
     * @return
     */
    public static String chooseDLMSUrl(String environment) {
        if (!isNullOrEmpty(environment)) {
            if (DLMS_ENV_MAP.keySet().contains(environment)) {
                return DLMS_ENV_MAP.get(environment);
            } else {
                String api = DLMS_ENV_MAP.get("production").replace("ng", environment);
                return api;
            }
        }
        return DLMS_ENV_MAP.get("production");
    }

    /**
     * choose DRA Url for different environment (production, stage1, new, dev)
     * @param environment
     * @return
     */
    public static String chooseDRAUrl(String environment) {
        if (!isNullOrEmpty(environment)) {
            if (GATE_DECISION_ENV_MAP.keySet().contains(environment)) {
                return GATE_DECISION_ENV_MAP.get(environment);
            } else {
                String api = GATE_DECISION_ENV_MAP.get("production").replace("ng", environment);
                return api;
            }
        }
        return GATE_DECISION_ENV_MAP.get("production");
    }

    /**
     * choose control center Url for different environment (production, stage1, new, dev)
     * @param environment
     * @return
     */
    public static String chooseControlCenterUrl(String environment) {
        if (!isNullOrEmpty(environment)) {
            if (CONTROL_CENTER_ENV_MAP.keySet().contains(environment)) {
                return CONTROL_CENTER_ENV_MAP.get(environment);
            } else {
                String api = CONTROL_CENTER_ENV_MAP.get("production").replace("ng", environment);
                return api;
            }
        }
        return CONTROL_CENTER_ENV_MAP.get("production");
    }

    /**
     * get IAM or UAA token based on the credentials id, only used by freestyle job's test connection
     * @param iamAPI
     * @param targetAPI
     * @param credentials
     * @return IAM token when user is using apikey, otherwise return UAA token
     * @throws Exception
     */
    public static String getTokenForFreeStyleJob(StandardCredentials credentials, String iamAPI, String targetAPI, PrintStream printStream) throws Exception {
        try {
            if (credentials instanceof StandardUsernamePasswordCredentials) {
                // if it is the Jenkins username/password type
                StandardUsernamePasswordCredentials c = (StandardUsernamePasswordCredentials) credentials;
                if (c.getUsername().equals("apikey")) {
                    // user is using apikey, get IAM token
                    return getIAMToken(c.getPassword().getPlainText(), iamAPI, printStream);
                } else {
                    // user is using username/pw, get UAA token
                    return getBluemixToken(c.getUsername(), c.getPassword().getPlainText(), targetAPI, printStream);
                }
            } else {
                // if it is the standard type
                StringCredentialsImpl value = (StringCredentialsImpl) credentials;
                return getIAMToken(value.getSecret().getPlainText(), iamAPI, printStream);
            }
        } catch (Exception e) {
            throw new Exception(getMessage(LOGIN_IN_FAIL) + e.getMessage());
        }
    }

    /**
     * get token for either pipeline or freestyle in the runtime
     * @param credentialsId
     * @param apikey
     * @param username
     * @param password
     * @param env
     * @param context
     * @return
     * @throws Exception
     */
    public static String getIBMCloudToken(String credentialsId, String apikey, String username, String password, String env, Job context, PrintStream printStream) throws Exception {
        String bluemixToken;
        String targetAPI = chooseTargetAPI(env);
        String iamAPI = chooseIAMAPI(env);

        try {
            if (isNullOrEmpty(credentialsId)) {
                // pipeline script
                if (isNullOrEmpty(apikey)) {
                    // user is still using username/password in the pipeline
                    bluemixToken = getBluemixToken(username, password, targetAPI, printStream);
                } else {
                    bluemixToken = getIAMToken(apikey, iamAPI, printStream);
                }
            } else {
                // freestyle job, find the credential and then get the token
                StandardCredentials credentials = findCredentials(credentialsId, context);
                bluemixToken = getTokenForFreeStyleJob(credentials, iamAPI, targetAPI, printStream);
            }
            return bluemixToken;
        } catch (Exception e) {
            throw new Exception(getMessage(LOGIN_IN_FAIL) + e.getMessage());
        }
    }

    /**
     * given the username and password, get the UAA token
     * @param username
     * @param password
     * @param targetAPI
     * @return
     * @throws MalformedURLException
     * @throws CloudFoundryException
     */
    public static String getBluemixToken(String username, String password, String targetAPI, PrintStream printStream) throws MalformedURLException, CloudFoundryException {
        CloudCredentials cloudCredentials = new CloudCredentials(username, password);
        URL url = new URL(targetAPI);
        HttpProxyConfiguration configuration = buildProxyConfiguration(url);

        CloudFoundryClient client = new CloudFoundryClient(cloudCredentials, url, configuration, true);
        if (printStream != null) {
            printStream.println(getMessageWithPrefix(USERNAME_PASSWORD_DEPRECATED));
            printStream.println(getMessageWithPrefix(LOGIN_IN_SUCCEED));
        }
        return "bearer " + client.login().toString();
    }

    /**
     * get IAM token using API key
     * @param apikey
     * @param iamAPI
     * @return
     * @throws Exception
     */
    public static String getIAMToken(String apikey, String iamAPI, PrintStream printStream) throws Exception {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost post = new HttpPost(iamAPI);
        post = addProxyInformation(post);
        post.addHeader("Content-Type", "application/x-www-form-urlencoded");
        post.addHeader("Accept", "application/json");

        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("grant_type", IAM_GRANT_TYPE));
        params.add(new BasicNameValuePair("response_type", IAM_RESPONSE_TYPE));
        params.add(new BasicNameValuePair("apikey", apikey));
        post.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));

        CloseableHttpResponse response = httpClient.execute(post);
        String res = EntityUtils.toString(response.getEntity());

        if (response.getStatusLine().toString().contains("200")) {
            //get 200 response
            JsonParser parser = new JsonParser();
            JsonElement element = parser.parse(res);
            JsonObject obj = element.getAsJsonObject();
            if (obj != null && obj.has("access_token")) {
                if (printStream != null) {
                    printStream.println(getMessageWithPrefix(LOGIN_IN_SUCCEED));
                }
                return "Bearer " + obj.get("access_token").toString().replace("\"", "");
            }
        }
        throw new Exception(getMessage(FAIL_TO_GET_API_TOKEN) + response.getStatusLine());
    }

    /**
     * set DLMS endpoint url to upload data to DLMS
     * @param baseUrl
     * @param toolchainId
     * @param appName
     * @param buildId
     * @return
     * @throws UnsupportedEncodingException
     */
    public static String setDLMSUrl(String baseUrl, String toolchainId, String appName, String buildId) throws UnsupportedEncodingException {
        String url = baseUrl;
        url = url.replace("{toolchain_id}", URLEncoder.encode(toolchainId, "UTF-8").replaceAll("\\+", "%20"));
        url = url.replace("{build_artifact}", URLEncoder.encode(appName, "UTF-8").replaceAll("\\+", "%20"));
        if (!isNullOrEmpty(buildId)) {
            url = url.replace("{build_id}", URLEncoder.encode(buildId, "UTF-8").replaceAll("\\+", "%20"));
        }

        return url;
    }

    /**
     * get build number if it is not specified
     * @param envVars
     * @param jobName
     * @param build
     * @return
     */
    public static String getBuildNumber(EnvVars envVars, String jobName, Run build, PrintStream printStream) throws Exception {
        // locate the build job that triggers current build, for the freestyle job
        Run triggeredBuild = getTriggeredBuild(build, jobName, envVars, printStream);
        if (triggeredBuild == null) {
            //failed to find the build job
            throw new Exception(getMessage(FAIL_TO_FIND_BUILD_JOB));
        } else {
            String buildJobName = isNullOrEmpty(jobName) ? envVars.get("JOB_NAME") : jobName;
            return constructBuildNumber(buildJobName, triggeredBuild);
        }
    }

    /**
     * construct the build number, format will be the jobName:buildNumber
     * @param build
     * @return
     */
    public static String constructBuildNumber(String jobName, Run build) {
        String jName = "";
        Scanner s = new Scanner(jobName).useDelimiter("/");
        while(s.hasNext()){ // this will loop through the string until the last string(job name) is reached.
            jName = s.next();
        }
        s.close();
        String buildNumber = jName + ":" + build.getNumber();
        return buildNumber;
    }

    /**
     * expand the env var and check if it is a required var
     * @param key
     * @param envVars
     * @param isRequired
     * @return
     * @throws Exception
     */
    public static String expandVariable(String key, EnvVars envVars, boolean isRequired) throws Exception {
        String val = envVars.expand(key);
        if (isRequired && isNullOrEmpty(val)) {
            throw new Exception(getMessage(MISS_CONFIGURATIONS));
        }
        return val;
    }

    public static String getOrgId(String token, String orgName, String environment, Boolean debug_mode) {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        String organizations_url = chooseOrganizationsUrl(environment);
        if(debug_mode){
            LOGGER.info("GET ORG_GUID URL:" + organizations_url + orgName);
        }

        try {
            HttpGet httpGet = new HttpGet(organizations_url + URLEncoder.encode(orgName, "UTF-8").replaceAll("\\+", "%20"));

            httpGet = addProxyInformation(httpGet);

            httpGet.setHeader("Authorization", token);
            CloseableHttpResponse response = null;

            response = httpClient.execute(httpGet);
            String resStr = EntityUtils.toString(response.getEntity());

            if(debug_mode){
                LOGGER.info("RESPONSE FROM ORGANIZATIONS API:" + response.getStatusLine().toString());
            }
            if (response.getStatusLine().toString().contains("200")) {

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
                    LOGGER.info("RETURNED STATUS CODE OTHER THAN 200. RESPONSE: " + response.getStatusLine().toString());
                }
                return null;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    public static String getSpaceId(String token, String spaceName, String environment, Boolean debug_mode) {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        String spaces_url = chooseSpacesUrl(environment);
        if(debug_mode){
            LOGGER.info("GET SPACE_GUID URL:" + spaces_url + spaceName);
        }

        try {
            HttpGet httpGet = new HttpGet(spaces_url + URLEncoder.encode(spaceName, "UTF-8").replaceAll("\\+", "%20"));

            httpGet = addProxyInformation(httpGet);

            httpGet.setHeader("Authorization", token);
            CloseableHttpResponse response = null;

            response = httpClient.execute(httpGet);
            String resStr = EntityUtils.toString(response.getEntity());

            if(debug_mode){
                LOGGER.info("RESPONSE FROM SPACES API:" + response.getStatusLine().toString());
            }
            if (response.getStatusLine().toString().contains("200")) {

                JsonParser parser = new JsonParser();
                JsonElement element = parser.parse(resStr);
                JsonObject obj = element.getAsJsonObject();
                JsonArray resources = obj.getAsJsonArray("resources");

                if(resources.size() > 0) {
                    JsonObject resource = resources.get(0).getAsJsonObject();
                    JsonObject metadata = resource.getAsJsonObject("metadata");
                    if(debug_mode){
                        LOGGER.info("SPACE_ID:" + String.valueOf(metadata.get("guid")).replaceAll("\"", ""));
                    }
                    return String.valueOf(metadata.get("guid")).replaceAll("\"", "");
                }
                else {
                    if(debug_mode){
                        LOGGER.info("RETURNED NO SPACES.");
                    }
                    return null;
                }

            } else {
                if(debug_mode){
                    LOGGER.info("RETURNED STATUS CODE OTHER THAN 200. RESPONSE: " + response.getStatusLine().toString());
                }
                return null;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    public static String getAppId(String token, String appName, String orgName, String spaceName, String environment, Boolean debug_mode) {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        String apps_url = chooseAppsUrl(environment);
        if(debug_mode){
            LOGGER.info("GET APPS_GUID URL:" + apps_url + appName + ORG + orgName + SPACE + spaceName);
        }

        try {
            HttpGet httpGet = new HttpGet(apps_url + URLEncoder.encode(appName, "UTF-8").replaceAll("\\+", "%20") + ORG + URLEncoder.encode(orgName, "UTF-8").replaceAll("\\+", "%20") + SPACE + URLEncoder.encode(spaceName, "UTF-8").replaceAll("\\+", "%20"));

            httpGet = addProxyInformation(httpGet);

            httpGet.setHeader("Authorization", token);
            CloseableHttpResponse response = null;

            response = httpClient.execute(httpGet);
            String resStr = EntityUtils.toString(response.getEntity());

            if(debug_mode){
                LOGGER.info("RESPONSE FROM APPS API:" + response.getStatusLine().toString());
            }
            if (response.getStatusLine().toString().contains("200")) {

                JsonParser parser = new JsonParser();
                JsonElement element = parser.parse(resStr);
                JsonObject obj = element.getAsJsonObject();
                JsonArray resources = obj.getAsJsonArray("resources");

                if(resources.size() > 0) {
                    JsonObject resource = resources.get(0).getAsJsonObject();
                    JsonObject metadata = resource.getAsJsonObject("metadata");
                    if(debug_mode){
                        LOGGER.info("APP_ID:" + String.valueOf(metadata.get("guid")).replaceAll("\"", ""));
                    }
                    return String.valueOf(metadata.get("guid")).replaceAll("\"", "");
                }
                else {
                    if(debug_mode){
                        LOGGER.info("RETURNED NO APPS.");
                    }
                    return null;
                }

            } else {
                if(debug_mode){
                    LOGGER.info("RETURNED STATUS CODE OTHER THAN 200. RESPONSE: " + response.getStatusLine().toString());
                }
                return null;
            }

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Get a list of policies that belong to a toolchain
     * @param token
     * @param toolchainId
     * @param environment
     * @param debug_mode
     * @return
     */
    public static ListBoxModel getPolicyList(String token, String toolchainId, String environment, Boolean debug_mode) {
        // get all jenkins job
        ListBoxModel emptybox = new ListBoxModel();
        emptybox.add("","empty");
        String url = choosePoliciesUrl(environment);

        try {
            url = url.replace("{toolchain_name}", URLEncoder.encode(toolchainId, "UTF-8").replaceAll("\\+", "%20"));
            CloseableHttpClient httpClient = HttpClients.createDefault();
            HttpGet httpGet = new HttpGet(url);
            httpGet = addProxyInformation(httpGet);
            httpGet.setHeader("Authorization", token);
            CloseableHttpResponse response = httpClient.execute(httpGet);
            String resStr = EntityUtils.toString(response.getEntity());

            if(debug_mode){
                LOGGER.info("GET POLICIES URL:" + url);
                LOGGER.info("RESPONSE FROM GET POLICIES URL:" + response.getStatusLine().toString());
            }
            if (response.getStatusLine().getStatusCode() == 200) {

                JsonParser parser = new JsonParser();
                JsonElement element = parser.parse(resStr);
                JsonArray jsonArray = element.getAsJsonArray();
                ListBoxModel model = new ListBoxModel();

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
                    LOGGER.info("RETURNED STATUS CODE OTHER THAN 200. RESPONSE: " + response.getStatusLine().toString());
                }
                return emptybox;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return emptybox;
    }

    /**
     * Get gate decision from DevOps Insights
     * @param bluemixToken
     * @param buildId
     * @param applicationName
     * @param toolchainId
     * @param environmentName
     * @param draUrl
     * @param policyName
     * @param printStream
     * @return
     * @throws IOException
     */
    public static JsonObject getDecisionFromDRA(String bluemixToken, String buildId, String applicationName, String toolchainId,
                                                String environmentName, String draUrl, String policyName, PrintStream printStream) throws IOException {
        // create http client and post method
        CloseableHttpClient httpClient = HttpClients.createDefault();

        String url = draUrl;
        url = url + "/toolchainids/" + toolchainId +
                "/buildartifacts/" + URLEncoder.encode(applicationName, "UTF-8").replaceAll("\\+", "%20") +
                "/builds/" + buildId +
                "/policies/" + URLEncoder.encode(policyName, "UTF-8").replaceAll("\\+", "%20") +
                "/decisions";
        if (!Util.isNullOrEmpty(environmentName)) {
            url = url.concat("?environment_name=" + environmentName);
        }

        HttpPost postMethod = new HttpPost(url);
        postMethod = addProxyInformation(postMethod);
        postMethod.setHeader("Authorization", bluemixToken);
        postMethod.setHeader("Content-Type", CONTENT_TYPE_JSON);
        CloseableHttpResponse response = httpClient.execute(postMethod);
        String resStr = EntityUtils.toString(response.getEntity());

        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode == 200) {
            JsonParser parser = new JsonParser();
            JsonElement element = parser.parse(resStr);
            JsonObject resJson = element.getAsJsonObject();
            printStream.println(getMessageWithPrefix(GET_DECISION_SUCCESS));
            return resJson;
        } else if (statusCode == 401 || statusCode == 403) {
            // if gets 401 or 403, it returns html
            printStream.println(getMessageWithVar(FAIL_TO_GET_DECISION, String.valueOf(statusCode), toolchainId));
        } else {
            JsonParser parser = new JsonParser();
            JsonElement element = parser.parse(resStr);
            JsonObject resJson = element.getAsJsonObject();
            if (resJson != null && resJson.has("message")) {
                printStream.println(getMessageWithVar(FAIL_TO_GET_DECISION_WITH_REASON, String.valueOf(statusCode), resJson.get("message").getAsString()));
            }
        }
        return null;
    }

    /**
     * publish the decision to Jenkins UI and fail the build/job/pipeline if needed
     * @param obj
     * @param build
     * @param reportUrl
     * @param ccUrl
     * @param policyName
     * @param willDisrupt
     * @param printStream
     */
    public static void publishDecision(JsonObject obj, Run build, String reportUrl, String ccUrl, String policyName,
                                       boolean willDisrupt, PrintStream printStream) {
        // retrieve the decision id to compose the report link
        String decisionId = String.valueOf(obj.get("decision_id"));
        decisionId = decisionId.replace("\"","");

        // Show Proceed or Failed based on the decision
        String decision = String.valueOf(obj.get("contents").getAsJsonObject().get("proceed"));
        if (decision.equals("true")) {
            decision = "Pass";
        } else {
            decision = "Fail";
        }

        String url = reportUrl + decisionId;
        GatePublisherAction action = new GatePublisherAction(url, ccUrl, decision, policyName, build);
        build.addAction(action);
        printStream.println(getMessageWithVar(DECISION_REPORT, reportUrl, ccUrl, decision));

        // Stop the build
        if (willDisrupt && decision.equals("Fail")) {
            Result result = Result.FAILURE;
            build.setResult(result);
        }
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
                        if (!isNullOrEmpty(bluemixToken)) {
                            envVars.put("DI_BM_TOKEN", bluemixToken);
                        }

                        if (!isNullOrEmpty(buildId)) {
                            envVars.put("DI_BUILD_ID", buildId);
                        }
                    }
                }
            }
        );
    }
}
