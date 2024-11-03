package jira;

import model.Release;
import model.Ticket;
import org.eclipse.jgit.revwalk.RevCommit;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import static jira.JiraRelease.*;
import static utils.JSON.readJsonFromUrl;

public class JiraTicket {

    private static final String FIELDS = "fields";

    private JiraTicket() {
        throw new IllegalStateException("Utility class");
    }

    public static List<Ticket> getTickets(String project) throws IOException {
        Integer j = 0;
        Integer i = 0;
        Integer total = 1;

        Integer injectionVersion = null;

        List<Release> releaseList = getRelease(project);
        List<Ticket> tickets = new ArrayList<>();

        HashMap<Integer, Release> releseIDMap = new HashMap<>();

        for(Release relese: releaseList) {
            releseIDMap.put(relese.getId(), relese);
        }

        do {
            j = i + 1000;
            String url = "https://issues.apache.org/jira/rest/api/2/search?jql=project=%22"
                    + project + "%22AND%22issueType%22=%22Bug%22AND(%22status%22=%22closed%22OR"
                    + "%22status%22=%22resolved%22)AND%22resolution%22=%22fixed%22&fields=key,resolutiondate,versions,created&startAt="
                    + i.toString() + "&maxResults=" + j.toString();
            JSONObject json = readJsonFromUrl(url);
            JSONArray issues = json.getJSONArray("issues");
            total = json.getInt("total");
            for (; i < total && i < j; i++) {
                JSONObject issue = issues.getJSONObject(i % 1000);
                String key = issue.get("key").toString();

                LocalDateTime openDate = LocalDateTime.parse(issue.getJSONObject(FIELDS).getString("created").substring(0, 16));
                LocalDateTime resolutionDate = LocalDateTime.parse(issue.getJSONObject(FIELDS).getString("resolutiondate").substring(0, 16));
                JSONArray affectedVersions = issue.getJSONObject(FIELDS).getJSONArray("versions");

                injectionVersion = null;


                if (affectedVersions.length() > 0) {
                    injectionVersion = affectedVersions.getJSONObject(0).getInt("id");
                    if(releseIDMap.containsKey(injectionVersion)) {
                        injectionVersion = releseIDMap.get(injectionVersion).getIndex();
                    }
                }

                Ticket ticket = new Ticket(key, openDate, resolutionDate, injectionVersion);
                tickets.add(ticket);

            }
        } while (i < total);

        for(Ticket ticket: tickets) {
            ticket.setOpeningVersion(getOV(ticket, releaseList));
            ticket.setFixedVersion(getFV(ticket, releaseList));
        }




        return tickets;
    }

    public static Integer getOV(Ticket ticket, List<Release> releases) {
        for (Release release : releases) {
            if(ticket.getOpeningDate().compareTo(release.getReleaseDate()) < 0) {
                return release.getIndex();
            }
        }
        return null;
    }

    public static Integer getFV(Ticket ticket, List<Release> releases) {
        for (Release release : releases) {
            if(ticket.getResolutionDate().compareTo(release.getReleaseDate()) < 0) {
                return release.getIndex();
            }
        }
        return null;
    }

    public static void commitsOfTheticket(List<RevCommit> commits, List<Ticket> tickets){
        for (Ticket ticket: tickets) {
            for (RevCommit commit: commits) {
                if (commit.getShortMessage().matches(".*\\b" + Pattern.quote(ticket.getId()) + "\\b.*")){
                    ticket.getCommits().add(commit);
                }
            }
        }
    }

    public static void removeTicketWithoutCommit(List<Ticket> tickets) {
        List<Ticket> ticketsToRemove = new ArrayList<>();
        for (Ticket ticket: tickets) {
            if (ticket.getCommits().isEmpty()){
                ticketsToRemove.add(ticket);
            }
        }
        tickets.removeAll(ticketsToRemove);
    }

    public static void fixTicketList(List<Ticket> tickets) {
        Iterator<Ticket> ticketIterator = tickets.iterator();
        while(ticketIterator.hasNext()) {
            Ticket ticket = ticketIterator.next();
            if(ticket.getFixedVersion() == null || ticket.getOpeningVersion() == null || ticket.getOpeningVersion() > ticket.getFixedVersion()) {
                ticketIterator.remove();
            } else if(ticket.getInjectedVersion() != null && ticket.getInjectedVersion() > ticket.getOpeningVersion()) {
                ticket.setInjectedVersion(null);
            } else if(ticket.getInjectedVersion() != null && ticket.getInjectedVersion().equals(ticket.getFixedVersion())) {
                ticketIterator.remove();
            }
        }
    }

    public static void calculateAV(Ticket ticket) {
        if (ticket.getInjectedVersion() != null && ticket.getFixedVersion() != null) {
            List<Integer> affectedVersions = new ArrayList<>();
            for (int version = ticket.getInjectedVersion(); version < ticket.getFixedVersion(); version++) {
                affectedVersions.add(version);
            }
            ticket.setAffectedVersion(affectedVersions);
        }
    }



}
