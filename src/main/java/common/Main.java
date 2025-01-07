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

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static weka.WekaController.calculateWeka;

public class Main {

    private final static String PATH = "fileCSV/";

    public static void main(String[] args) {
        try {
            runProjectPipeline("BOOKKEEPER", ConfigLoader.getBookkeeperPath());
            runProjectPipeline("SYNCOPE", ConfigLoader.getSyncopePath());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    private static void runProjectPipeline(String project, String path) throws IOException {
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


        WriteCSV.writeReleasesForWalkForward(releases, tickets, PATH + project + "/training/file", PATH + project + "/testing/file", path);

        WekaController.convertAllCsvInFolder(PATH + project + "/training");
        WekaController.convertAllCsvInFolder(PATH + project + "/testing");

        calculateWeka(project, releases.size());

        WriteCSV.cleanDirectory(PATH + project + "/training");
        WriteCSV.cleanDirectory(PATH + project + "/testing");
        WriteCSV.cleanDirectory("csv");

    }
}
