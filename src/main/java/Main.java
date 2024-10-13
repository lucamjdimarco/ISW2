import git.GitController;
import jira.JiraRelease;
import jira.JiraTicket;
import model.*;
import org.eclipse.jgit.revwalk.DepthWalk;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

import static jira.JiraTicket.commitsOfTheticket;


public class Main {

    public static void main(String[] args) {
        try {
            List<Release> releases = new ArrayList<>();
            List<Ticket> tickets = new ArrayList<>();
            List<RevCommit> commits = new ArrayList<>();

            releases = JiraRelease.getRelease("BOOKKEEPER");
            tickets = JiraTicket.getTickets("BOOKKEEPER");
            commits = GitController.retrieveCommits("/Users/lucadimarco/Desktop/bookkeeper/bookkeeper");

            commitsOfTheticket(commits, tickets);

            for(Ticket ticket: tickets){
                System.out.println("Ticket: " + ticket);
            }




            /* ----- SCRITTURA ---- */

            FileWriter fileWriter = new FileWriter("releases.csv");

            // Scrivi l'intestazione del CSV
            fileWriter.append("VERSION, FILE_NAME, LOC, LOC_TOUCHED, NUMBER_OF_REVISIONS, LOC_ADDED, AVG_LOC_ADDED, NUMBER_OF_AUTHORS, MAX_LOC_ADDED, TOTAL_LOC_REMOVED, MAX_LOC_REMOVED, AVG_LOC_TOUCHED\n");

            //int i = 0;

            // Itera attraverso le release e scrivi i dati
            for (Release release : releases) {
                for (FileJava file : release.getFiles()) {
                    // Scrivi i dati nel formato CSV
                    fileWriter.append(release.getIndex() + ",");
                    //fileWriter.append(release.getReleaseDate().toString() + ",");
                    fileWriter.append(file.getName() + ",");
                    fileWriter.append(file.getLoc() + ",");
                    fileWriter.append(file.getLocTouched() + ",");
                    fileWriter.append(file.getNr() + ",");
                    fileWriter.append(file.getLocAdded() + ",");
                    fileWriter.append(file.getAvgLocAdded() + ",");
                    fileWriter.append(file.getNauth() + ",");
                    fileWriter.append(file.getMaxLocAdded() + ",");
                    fileWriter.append(file.getTotalLocRemoved() + ",");
                    fileWriter.append(file.getMaxLocRemoved() + ",");
                    fileWriter.append(file.getAvgLocTouched() + "\n");
                }


            }


            //ci mancano file nell'ultima release nel CSV nwlla lista ci sono 
            //System.out.println("Last file of last release: " + releases.get(releases.size() - 2).getFiles().get(releases.get(releases.size() - 2).getFiles().size() - 2));

        } catch (Exception e) {
            e.printStackTrace();
        }


    }
}
