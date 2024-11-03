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

            String project = "SYNCOPE";
            String path = ConfigLoader.getSyncopePath();

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

            /*for(Ticket ticket: tickets){
                System.out.println("Ticket " + ticket);
            }*/

            String subpath = "fileCSV/";

            WriteCSV.writeReleasesForWalkForward(project, releases, tickets, subpath + project + "/training/file", subpath + project + "/testing/file", path);

            WekaController.convertAllCsvInFolder(subpath + project + "/training");
            WekaController.convertAllCsvInFolder(subpath + project + "/testing");

            calculateWeka(project, releases.size());

        } catch (Exception e) {
            e.printStackTrace();
        }


    }
}
