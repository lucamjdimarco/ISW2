import git.GitController;
import jira.JiraRelease;
import jira.JiraTicket;
import model.*;

import org.eclipse.jgit.revwalk.RevCommit;
import utils.CalculateBugginess;
import utils.Proportion;
import utils.WriteCSV;
import weka.WekaController;

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

            WriteCSV.writeReleasesForWalkForward(releases, "fileCSV/training/file", "fileCSV/testing/file");

            /*System.out.println(" ------- CONVERSIONE CSV TO ARFF ------- ");

            for(int i = 1; i < releases.size(); i++) {
                WekaController.convertCSVtoARFF("fileCSV/training/file_train_step_" + i + ".csv", "fileARFF/training/file_train_step_" + i + ".arff");
                WekaController.convertCSVtoARFF("fileCSV/testing/file_test_step_" + i + ".csv", "fileARFF/testing/file_test_step_" + i + ".arff");
            }*/

            System.out.println(" ------- FINE ------- ");

        } catch (Exception e) {
            e.printStackTrace();
        }


    }
}
