package com.ibm.devops.dra;

public class DeploymentInfoModel {
	private String app_url;
	private String environment_name;
	private String job_url;
	private String status;
	private String timestamp;

	public DeploymentInfoModel(String app_url, String environment_name, String job_url, String status, String timestamp) {
		this.app_url = app_url;
		this.environment_name = environment_name;
		this.job_url = job_url;
		this.status = status;
		this.timestamp = timestamp;
	}
}
