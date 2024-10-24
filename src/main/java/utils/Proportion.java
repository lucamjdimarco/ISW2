package utils;

import jira.JiraTicket;
import model.Ticket;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class Proportion {

    private static List<String> projForColdStart = new ArrayList<>();

    private static int movWinSize;
    private static double prop;
    private static final List<Ticket> ticketofProportion=new ArrayList<>();

    private Proportion() {
        throw new IllegalStateException("Utility class");
    }

    public static void getProportion(List<Ticket> tickets, String project) throws IOException {

        List<String> projectsToAdd = new ArrayList<>();

        if(project.equals("BOOKKEEPER")) {
            projectsToAdd = List.of("AVRO", "ZOOKEEPER");
        } else if(project.equals("SYNCOPE")) {
            projectsToAdd = List.of("PROTON", "OPENJPA");
        } else {
            throw new IllegalArgumentException("Project not found");
        }

        //List<String> projectsToAdd = List.of("AVRO", "OPENJPA", "ZOOKEEPER", "STORM", "TAJO");
        //List<String> projectsToAdd = List.of("AVRO");
        projForColdStart.addAll(projectsToAdd);

        int numTickets=tickets.size();
        movWinSize=Math.max(1, numTickets / 10); //uso il 10% dei ticket, ma almeno 1 ticket
        prop=0;


        for (Ticket ticket: tickets) {
            if (ticket.getInjectedVersion() != null ) {
                //per rispettare la grandezza della finestra mobile
                //rimuovo il ticket più vecchio e inserisco
                if(ticketofProportion.size() > movWinSize){
                    ticketofProportion.remove(0);
                }
                ticketofProportion.add(ticket);
            } else {
                //sono all'inizio e uso cold start
                if (ticketofProportion.size() < movWinSize) {
                    if (prop==0) {
                        //prop = coldStart();
                        prop = coldStart();
                    }
                } else {
                    prop = movingWindow();

                }

                setInjectedVersion(ticket);
            }
        }
    }

    public static void setInjectedVersion(Ticket ticket) {

        if (ticket.getFixedVersion() == ticket.getOpeningVersion()) {
            ticket.setInjectedVersion((int) Math.floor(ticket.getFixedVersion()-prop));
        }else {
            ticket.setInjectedVersion((int) Math.floor((ticket.getFixedVersion() - (ticket.getFixedVersion() - ticket.getOpeningVersion()) * prop)));
        }
        if (ticket.getInjectedVersion()<=0) {
            ticket.setInjectedVersion(1);
        }
    }

    public static double movingWindow() {
        int k=0;
        double p = 0;
        for(Ticket t : ticketofProportion) {
            if(t.getFixedVersion() != t.getOpeningVersion()) {
                p += (double) (t.getFixedVersion() - t.getInjectedVersion()) / (t.getFixedVersion() - t.getOpeningVersion());
            }else{
                //evito la divisione per zero
                p+= (t.getFixedVersion() - t.getInjectedVersion());
            }
            k++;
        }

        if(k!=0) {
            p = p / k;
        }

        return p;
    }

    public static double coldStart() throws IOException {
        //utilizzo i ticket di progetti diversi per fare il cold start
        List<Ticket> tickets = new ArrayList<>();
        List<Double> prop_calc = new ArrayList<>();
        double p = 0;
        int counter;

        //calcolo la proportion per ogni progetto e lo inserisco nella lista
        //utilizzo la media delle proporzioni per fare il cold start

        for(String proj : projForColdStart) {
            counter = 0;
            tickets.clear();
            tickets = JiraTicket.getTickets(proj);
            for(Ticket ticket: tickets){
                if(ticket.getInjectedVersion() != null && ticket.getOpeningVersion() != null && ticket.getFixedVersion() != null
                        && ticket.getOpeningVersion() != ticket.getFixedVersion()){
                    if((double) (ticket.getFixedVersion() - ticket.getInjectedVersion()) / (ticket.getFixedVersion() - ticket.getOpeningVersion()) > 1 ) {
                        p += (double) (ticket.getFixedVersion() - ticket.getInjectedVersion()) / (ticket.getFixedVersion() - ticket.getOpeningVersion());
                    }else{
                        p += 1;
                    }
                    counter++;
                }
            }
            if(counter != 0) {
                p = p / counter;
                prop_calc.add(p);
            }

        }


        /*prop_calc.sort(Comparator.naturalOrder());
        if(prop_calc.size() % 2 == 0) {
            return ((prop_calc.get(prop_calc.size() / 2) + prop_calc.get(prop_calc.size() / 2 - 1)) / 2);
        } else {
            return (prop_calc.get(prop_calc.size() / 2));
        }*/
        //restituisco al media delle proportion
        return prop_calc.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

}
