package utils;

import acume.AcumeController;
import jira.JiraTicket;
import model.Ticket;

import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

import static java.util.logging.Level.SEVERE;

public class Proportion {

    private static final Logger logger = Logger.getLogger(Proportion.class.getName());

    private static List<String> projForColdStart = new ArrayList<>();

    private static int movWinSize;
    private static double prop;
    private static int numTickets;
    private static final List<Ticket> ticketofProportion=new ArrayList<>();

    private Proportion() {
        throw new IllegalStateException("Utility class");
    }

    public static void getProportion(List<Ticket> tickets, String project) throws IOException {
        List<String> projectsToAdd = initializeProjectsToAdd(project);
        projForColdStart.addAll(projectsToAdd);

        initializeParameters(tickets);

        for (Ticket ticket : tickets) {
            processTicket(ticket);
        }
    }

    private static List<String> initializeProjectsToAdd(String project) {
        if (project.equals("BOOKKEEPER")) {
            return List.of("AVRO", "ZOOKEEPER");
        } else if (project.equals("SYNCOPE")) {
            return List.of("PROTON", "OPENJPA");
        } else {
            throw new IllegalArgumentException("Project not found");
        }
    }

    private static void initializeParameters(List<Ticket> tickets) {
        int numTickets = tickets.size();
        movWinSize = Math.max(1, numTickets / 10); // Usa il 10% dei ticket, ma almeno 1 ticket
        prop = 0;
    }

    private static void processTicket(Ticket ticket) {
        if (ticket.getInjectedVersion() != null) {
            addToMovingWindow(ticket);
        } else {
            updateProportionIfNeeded();
            setInjectedVersion(ticket);
        }
    }

    private static void addToMovingWindow(Ticket ticket) {
        if (ticketofProportion.size() > movWinSize) {
            ticketofProportion.remove(0);
        }
        ticketofProportion.add(ticket);
    }

    private static void updateProportionIfNeeded() {
        try {
            if (ticketofProportion.size() < movWinSize) {
                if (prop == 0) {
                    prop = coldStart();
                }
            } else {
                prop = movingWindow();
            }
        } catch (IOException e) {
            logger.log(SEVERE, e.getMessage());
        }

    }


    public static void setInjectedVersion(Ticket ticket) {

        if (ticket.getFixedVersion().equals(ticket.getOpeningVersion())) {
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
        List<Ticket> tickets = new ArrayList<>();
        List<Double> prop_calc = new ArrayList<>();
        double p = 0;
        int counter;

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

        return prop_calc.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

}
