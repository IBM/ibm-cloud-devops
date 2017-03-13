/*
    <notice>

    Copyright 2016, 2017 IBM Corporation

     Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
    The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

    </notice>
 */

package draplugin.notification;

import jenkins.model.Jenkins;
import hudson.ProxyConfiguration;
import org.apache.http.HttpHost;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.config.RequestConfig;

/**
 * Utilities functions for notification plugin
 * Created by Xunrong Li on 7/22/16.
 */


public class Util {
    /**
     * check if the str is null or empty
     * @param str
     * @return true if it is null or empty
     */
    public static boolean isNullOrEmpty(String str) {
        if (str == null || str.isEmpty()) {
            return true;
        }
        return false;
    }

    public static HttpPost addProxyInformation (HttpPost instance) {
        /* Add proxy to request if proxy settings in Jenkins UI are set. */
        ProxyConfiguration proxyConfig = Jenkins.getInstance().proxy;
        if(proxyConfig != null){
            if((!isNullOrEmpty(proxyConfig.name)) && proxyConfig.port != 0) {
                HttpHost proxy = new HttpHost(proxyConfig.name, proxyConfig.port, "http");
                RequestConfig config = RequestConfig.custom().setProxy(proxy).build();
                instance.setConfig(config);
            }
        }
        return instance;
    }
}
