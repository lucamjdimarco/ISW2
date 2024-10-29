package common;

import git.GitController;
import jira.JiraRelease;
import jira.JiraTicket;
import model.*;

import org.eclipse.jgit.revwalk.RevCommit;
import utils.ConfigLoader;
import utils.Proportion;
import utils.WriteCSV;
import weka.WekaController;

import java.util.Collections;
import java.util.List;

import static weka.WekaController.calculateWeka;


public class Main {



    public static void main(String[] args) {
        try {

            String project = "BOOKKEEPER";
            String path = ConfigLoader.getBookkeeperPath();

            List<Release> releases;
            List<Ticket> tickets;
            List<RevCommit> commits;

            releases = JiraRelease.getRelease(project);
            tickets = JiraTicket.getTickets(project);

            commits = GitController.retrieveCommits(path);

            GitController.calculateMetric(releases, path);

            JiraTicket.commitsOfTheticket(commits, tickets);
            JiraTicket.removeTicketWithoutCommit(tickets);
            JiraTicket.fixTicketList(tickets);

            //PROPORTION
            Collections.reverse(tickets);

            Proportion.getProportion(tickets, project);
            for(Ticket ticket: tickets){
                JiraTicket.calculateAV(ticket);
            }

            WriteCSV.writeReleasesForWalkForward(releases, tickets, "fileCSV/" + project + "/training/file", "fileCSV/" + project + "/testing/file", path);

            WekaController.convertAllCsvInFolder("fileCSV/" + project + "/training");
            WekaController.convertAllCsvInFolder("fileCSV/" + project + "/testing");

            calculateWeka(project, releases.size());

        } catch (Exception e) {
            e.printStackTrace();
        }


    }
}
