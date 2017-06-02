/*
 <notice>

 Copyright 2017 IBM Corporation

 Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

 </notice>
 */

package com.ibm.devops.notification;


import com.ibm.devops.dra.Util;
import hudson.EnvVars;
import hudson.ProxyConfiguration;
import hudson.model.*;
import hudson.security.ACL;
import hudson.tasks.Recorder;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.cloudfoundry.client.lib.CloudCredentials;
import org.cloudfoundry.client.lib.CloudFoundryClient;
import org.cloudfoundry.client.lib.HttpProxyConfiguration;

import java.io.IOException;
import java.io.PrintStream;
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
 * OTCAPIHelper class helps collect various information (org, space, app) from OTC API
 */
public abstract class OTCAPIHelper extends Recorder {
	// API URLs
    private final static String ORGS_URL= "/v2/organizations?q=name:";
    private final static String SPACES_URL= "/v2/spaces?q=name:";
    private final static String APPS_URL= "/v2/apps?q=name:";
    private final static String ORG= "&&organization_guid:";
    private final static String SPACE= "&&space_guid:";
    

    public static void printPluginVersion(ClassLoader loader, PrintStream printStream) {
        final Properties properties = new Properties();
        try {
            properties.load(loader.getResourceAsStream("plugin.properties"));
            printStream.println("[IBM Cloud DevOps] version: " + properties.getProperty("version"));
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
    
    /*
     * Returns a valid API url (as env variable might be missing the leading https part)
     */
    public static String getTargetAPI(EnvVars envVars) {
    	String cf_api = envVars.get("CF_API");
    	if (cf_api.startsWith("https")) {
    		return cf_api;
    	}
    	return "https://" + cf_api;
    }
    
    /*
     * Returns a Bluemix token based on credentials as found in env variables
     */
    public static String getBluemixToken(EnvVars envVars, PrintStream printStream) {
        try {
        	String targetAPI = getTargetAPI(envVars);
        	String username = envVars.get("CF_CREDS_USR");
        	String password = envVars.get("CF_CREDS_PSW");
        	
        	CloudCredentials cloudCredentials = new CloudCredentials(username, password);

            URL url = new URL(targetAPI);
            HttpProxyConfiguration configuration = buildProxyConfiguration(url);

            CloudFoundryClient client = new CloudFoundryClient(cloudCredentials, url, configuration, true);
            return "bearer " + client.login().toString();

        } catch (Exception e) {
        	printStream.println("[IBM Cloud DevOps] Unexpected Exception:");
            e.printStackTrace(printStream);
        }
        return "";
    }
    
    /*
     * Returns all orgs from OTC API for the given Bluemix token
     */    
    public static JSONObject getOrgs(EnvVars envVars, String bluemixToken, PrintStream printStream) {
    	String orgName = envVars.get("CF_ORG");
    	String orgsUrl = getTargetAPI(envVars) + ORGS_URL + orgName;

    	CloseableHttpClient httpClient = HttpClients.createDefault();
    	HttpGet httpGet = new HttpGet(orgsUrl);

    	httpGet = addProxyInformation(httpGet);

    	httpGet.setHeader("Authorization", bluemixToken);
    	CloseableHttpResponse response = null;

    	try {
    		response = httpClient.execute(httpGet);
    		String resStr = EntityUtils.toString(response.getEntity());
    		if (response.getStatusLine().toString().contains("200")) {
    			// get 200 response
    			return JSONObject.fromObject(resStr);
    		}

    	} catch (Exception e) {
        	printStream.println("[IBM Cloud DevOps] Unexpected Exception:");
            e.printStackTrace(printStream);
        }
    	return new JSONObject();
    }
    
    /*
     * Returns all spaces from OTC API for the given Bluemix token
     */  
    public static JSONObject getSpaces(EnvVars envVars, String bluemixToken, PrintStream printStream) {
    	String spaceName = envVars.get("CF_SPACE");
    	String spacesUrl = getTargetAPI(envVars) + SPACES_URL + spaceName;

    	CloseableHttpClient httpClient = HttpClients.createDefault();
    	HttpGet httpGet = new HttpGet(spacesUrl);

    	httpGet = addProxyInformation(httpGet);

    	httpGet.setHeader("Authorization", bluemixToken);
    	CloseableHttpResponse response = null;

    	try {
    		response = httpClient.execute(httpGet);
    		String resStr = EntityUtils.toString(response.getEntity());
    		if (response.getStatusLine().toString().contains("200")) {
    			// get 200 response
    			return JSONObject.fromObject(resStr);
    		}

    	} catch (Exception e) {
        	printStream.println("[IBM Cloud DevOps] Unexpected Exception:");
            e.printStackTrace(printStream);
        }
    	return new JSONObject();
    }
    
    /*
     * Returns all matching apps from OTC API for the given Bluemix token, org and space
     */  
    public static JSONObject getApps(EnvVars envVars, String bluemixToken, PrintStream printStream) {
    	String orgName = envVars.get("CF_ORG");
    	String spaceName = envVars.get("CF_SPACE");
    	String appName = envVars.get("CF_APP");
    	String appsUrl = getTargetAPI(envVars) + APPS_URL + appName + ORG + orgName + SPACE + spaceName;
    	
    	CloseableHttpClient httpClient = HttpClients.createDefault();
    	HttpGet httpGet = new HttpGet(appsUrl);

    	httpGet = addProxyInformation(httpGet);

    	httpGet.setHeader("Authorization", bluemixToken);
    	CloseableHttpResponse response = null;

    	try {
    		response = httpClient.execute(httpGet);
    		String resStr = EntityUtils.toString(response.getEntity());
    		if (response.getStatusLine().toString().contains("200")) {
    			// get 200 response
    			return JSONObject.fromObject(resStr);
    		}

    	} catch (Exception e) {
        	printStream.println("[IBM Cloud DevOps] Unexpected Exception:");
            e.printStackTrace(printStream);
        }
    	return new JSONObject();
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

    public static HttpGet addProxyInformation (HttpGet instance) {
        /* Add proxy to request if proxy settings in Jenkins UI are set. */
        ProxyConfiguration proxyConfig = Jenkins.getInstance().proxy;
        if(proxyConfig != null){
            if((!Util.isNullOrEmpty(proxyConfig.name)) && proxyConfig.port != 0) {
                HttpHost proxy = new HttpHost(proxyConfig.name, proxyConfig.port, "http");
                RequestConfig config = RequestConfig.custom().setProxy(proxy).build();
                instance.setConfig(config);
            }
        }
        return instance;
    }

    public static HttpPost addProxyInformation (HttpPost instance) {
        /* Add proxy to request if proxy settings in Jenkins UI are set. */
        ProxyConfiguration proxyConfig = Jenkins.getInstance().proxy;
        if(proxyConfig != null){
            if((!Util.isNullOrEmpty(proxyConfig.name)) && proxyConfig.port != 0) {
                HttpHost proxy = new HttpHost(proxyConfig.name, proxyConfig.port, "http");
                RequestConfig config = RequestConfig.custom().setProxy(proxy).build();
                instance.setConfig(config);
            }
        }
        return instance;
    }

}
