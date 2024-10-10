import jira.JiraRelease;
import model.*;

import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;


public class Main {

    public static void main(String[] args) {
        try {
            List<Release> releases = new ArrayList<>();
            releases = JiraRelease.getRelease("BOOKKEEPER");

            /*for(Release r : releases) {
                System.out.println(r);
            }*/


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


                    //i++;
                }
                //System.out.println("Release: " + release.getIndex() + " " + i);
                //i = 0;

            }


            //ci mancano file nell'ultima release nel CSV nwlla lista ci sono 
            //System.out.println("Last file of last release: " + releases.get(releases.size() - 2).getFiles().get(releases.get(releases.size() - 2).getFiles().size() - 2));



            //JiraTicket.getTickets("BOOKKEEPER");

        } catch (Exception e) {
            e.printStackTrace();
        }


    }
}
