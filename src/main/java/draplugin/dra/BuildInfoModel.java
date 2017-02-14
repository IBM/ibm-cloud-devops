package draplugin.dra;

/**
 * Created by lix on 8/25/16.
 */
public class BuildInfoModel {
    private String build_id;
    private String job_url;
    private String status;
    private String timestamp;
    private Repo repository;

    public BuildInfoModel(String build_id, String job_url, String status, String timestamp, Repo repository) {
        this.build_id = build_id;
        this.job_url = job_url;
        this.status = status;
        this.timestamp = timestamp;
        this.repository = repository;
    }

    public static class Repo {
        private String repository_url;
        private String branch;
        private String commit_id;

        public Repo(String repository_url, String branch, String commit_id) {
            this.repository_url = repository_url;
            this.branch = branch;
            this.commit_id = commit_id;
        }
    }
}
