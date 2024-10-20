import git.GitController;
import jira.JiraRelease;
import jira.JiraTicket;
import model.*;

import org.eclipse.jgit.revwalk.RevCommit;
import utils.CalculateBugginess;
import utils.Proportion;
import utils.WriteCSV;

import java.util.Collections;
import java.util.List;


public class Main {

    public static void main(String[] args) {
        try {

            String project = "BOOKKEEPER";
            //String project = "SYNCOPE";

            String path = "/Users/lucadimarco/Desktop/bookkeeper/bookkeeper";
            //String path = "/Users/lucadimarco/Desktop/syncope/syncope";

            List<Release> releases;
            List<Ticket> tickets;
            List<RevCommit> commits;

            releases = JiraRelease.getRelease(project);
            tickets = JiraTicket.getTickets(project);

            commits = GitController.retrieveCommits(path);

            System.out.println(" ------- CALCOLO METRICHE ------- ");

            GitController.calculateMetric(releases, path);

            JiraTicket.commitsOfTheticket(commits, tickets);
            JiraTicket.removeTicketWithoutCommit(tickets);
            JiraTicket.fixTicketList(tickets);

            //PROPORTION
            Collections.reverse(tickets);

            System.out.println(" ------- CALCOLO PROPORTION ------- ");

            Proportion.getProportion(tickets);
            for(Ticket ticket: tickets){
                JiraTicket.calculateAV(ticket);
            }

            System.out.println(" ------- CALCOLO BUGGINESS ------- ");

            CalculateBugginess.markBuggyFilesUsingAffectedVersions(tickets, releases, path);

            System.out.println(" ------- SCRITTURA SU FILE ------- ");

            WriteCSV.writeReleasesToCsv(releases, "releases.csv");

            System.out.println(" ------- FINE ------- ");

        } catch (Exception e) {
            e.printStackTrace();
        }


    }
}
