import git.GitController;
import jira.JiraRelease;
import jira.JiraTicket;
import model.*;
import org.eclipse.jgit.revwalk.DepthWalk;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static jira.JiraTicket.*;
import static utils.Proportion.*;


public class Main {

    public static void main(String[] args) {
        try {
            List<Release> releases;
            List<Ticket> tickets;
            List<RevCommit> commits;

            //releases = JiraRelease.getRelease("BOOKKEEPER");
            releases = JiraRelease.getRelease("SYNCOPE");

            System.out.println("Release 1: " + releases.get(0).getReleaseDate());
            System.out.println("Release 4: " + releases.get(3).getReleaseDate());


            //tickets = JiraTicket.getTickets("BOOKKEEPER");
            tickets = JiraTicket.getTickets("SYNCOPE");
            //commits = GitController.retrieveCommits("/Users/lucadimarco/Desktop/bookkeeper/bookkeeper");
            commits = GitController.retrieveCommits("/Users/lucadimarco/Desktop/syncope/syncope");

            commitsOfTheticket(commits, tickets);
            removeTicketWithoutCommit(tickets);
            fixTicketList(tickets);

            //PROPORTION
            Collections.reverse(tickets);
            getProportion(tickets);
            for(Ticket ticket: tickets){
                calculateAV(ticket);
            }

            //CALCOLO DELLA BUGGY
            //processTickets(tickets, "/Users/lucadimarco/Desktop/bookkeeper/bookkeeper", releases);
            //processReleasesAndMarkBuggyFiles(releases, tickets, "/Users/lucadimarco/Desktop/bookkeeper/bookkeeper");
            //markBuggyFilesUsingAffectedVersions(tickets, releases, "/Users/lucadimarco/Desktop/bookkeeper/bookkeeper");
            markBuggyFilesUsingAffectedVersions(tickets, releases, "/Users/lucadimarco/Desktop/syncope/syncope");


            for(Ticket ticket: tickets){
                System.out.println("Ticket: " + ticket);
            }

            //RIATTIVARE IL CALCOLO DELLE METRICHE

            for(Release release: releases){
                for(FileJava file: release.getFiles()){
                    if(file.isBuggy()){
                        System.out.println("File: " + file.getName() + " is buggy");
                    }
                }
            }






            /* ----- SCRITTURA ---- */

            FileWriter fileWriter = new FileWriter("releases.csv");

            // Scrivi l'intestazione del CSV
            fileWriter.append("VERSION, FILE_NAME, LOC, LOC_TOUCHED, NUMBER_OF_REVISIONS, LOC_ADDED, AVG_LOC_ADDED, NUMBER_OF_AUTHORS, MAX_LOC_ADDED, TOTAL_LOC_REMOVED, MAX_LOC_REMOVED, AVG_LOC_TOUCHED, BUGGY\n");

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
                    fileWriter.append(file.getAvgLocTouched() + ",");
                    if(file.isBuggy()){
                        fileWriter.append("YES\n");
                    } else {
                        fileWriter.append("NO\n");
                    }

                }


            }


            //ci mancano file nell'ultima release nel CSV nwlla lista ci sono 
            //System.out.println("Last file of last release: " + releases.get(releases.size() - 2).getFiles().get(releases.get(releases.size() - 2).getFiles().size() - 2));

        } catch (Exception e) {
            e.printStackTrace();
        }


    }
}
