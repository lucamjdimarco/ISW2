package jira;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static jira.JiraRelease.releaseNames;
import static jira.JiraRelease.releases;
import static utils.JSON.readJsonFromUrl;

public class JiraTicket {

    private JiraTicket() {
        throw new IllegalStateException("Utility class");
    }

    public static void getTickets(String project) throws IOException {
        Integer j = 0, i = 0, total = 1;
        Integer injectionVersion;
        //Get JSON API for closed bugs w/ AV in the project
        do {
            //Only gets a max of 1000 at a time, so must do this multiple times if bugs >1000
            j = i + 1000;
            String url = "https://issues.apache.org/jira/rest/api/2/search?jql=project=%22"
                    + project + "%22AND%22issueType%22=%22Bug%22AND(%22status%22=%22closed%22OR"
                    + "%22status%22=%22resolved%22)AND%22resolution%22=%22fixed%22&fields=key,resolutiondate,versions,created&startAt="
                    + i.toString() + "&maxResults=" + j.toString();
            JSONObject json = readJsonFromUrl(url);
            JSONArray issues = json.getJSONArray("issues");
            total = json.getInt("total");
            for (; i < total && i < j; i++) {
                //Iterate through each bug
                //String key = issues.getJSONObject(i%1000).get("key").toString();
                //System.out.println(key);

                JSONObject issue = issues.getJSONObject(i % 1000);
                String key = issue.get("key").toString();

                LocalDateTime openDate = LocalDateTime.parse(issue.getJSONObject("fields").getString("created").substring(0, 16));
                JSONArray affectedVersions = issue.getJSONObject("fields").getJSONArray("versions");

                if (affectedVersions.length() > 0) {
                    for (int k = 0; k < affectedVersions.length(); k++) {
                        //se ho AV allora la IV sarà la prima versione
                        injectionVersion = affectedVersions.getJSONObject(0).getInt("id");
                    }
                }


            }
        } while (i < total);
        return;
    }
}
