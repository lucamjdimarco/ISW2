package utils;

import com.opencsv.CSVWriter;
import model.*;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.text.DecimalFormat;


public class WriteCSV {

    private WriteCSV() {
        throw new IllegalStateException("Utility class");
    }
    public static void writeReleasesForWalkForward(List<Release> releases, List<Ticket> tickets, String baseCsvFilePathForTraining, String baseCsvFilePathForTesting, String repo) {
        // Itera per ogni step del walk forward
        for (int i = 1; i < releases.size(); i++) {

            List<Release> releaseList = new ArrayList<>();
            List<Ticket> ticketList = new ArrayList<>();
            for (Release release : releases) {
                if (release.getIndex() <= i) {
                    releaseList.add(release);
                }
            }
            for (Ticket ticket : tickets) {
                if (ticket.getFixedVersion() < i) {
                    ticketList.add(ticket);
                }
            }

            CalculateBugginess.markBuggyFilesUsingAffectedVersions(ticketList, releaseList, repo);

            String trainingCsvFilePath = baseCsvFilePathForTraining + "_train_step_" + i + ".csv";
            writeReleasesToCsv(releases.subList(0, i), trainingCsvFilePath);

            CalculateBugginess.markBuggyFilesUsingAffectedVersions(tickets, releases.subList(i, i + 1), repo);

            String testingCsvFilePath = baseCsvFilePathForTesting + "_test_step_" + i + ".csv";
            writeReleasesToCsv(releases.subList(i, i + 1), testingCsvFilePath);
        }
    }

    /**
     * Scrive le release in un singolo file CSV.
     * @param releases Lista delle release da scrivere nel CSV.
     * @param csvFilePath Percorso del file CSV.
     */
    public static void writeReleasesToCsv(List<Release> releases, String csvFilePath) {
        try (CSVWriter writer = new CSVWriter(new FileWriter(csvFilePath))) {
            // Intestazione del CSV
            String[] header = { "VERSION", "FILE_NAME", "LOC", "LOC_TOUCHED", "NUMBER_OF_REVISIONS",
                    "LOC_ADDED", "AVG_LOC_ADDED", "NUMBER_OF_AUTHORS", "MAX_LOC_ADDED",
                    "TOTAL_LOC_REMOVED", "MAX_LOC_REMOVED", "AVG_LOC_TOUCHED", "BUGGY" };
            writer.writeNext(header);

            // Itera sulle release e sui file per scrivere i dati
            for (Release release : releases) {
                for (FileJava file : release.getFiles()) {
                    String[] fileData = {
                            String.valueOf(release.getIndex()),               // VERSION
                            file.getName(),                                   // FILE_NAME
                            String.valueOf(file.getLoc()),                    // LOC
                            String.valueOf(file.getLocTouched()),             // LOC_TOUCHED
                            String.valueOf(file.getNr()),                     // NUMBER_OF_REVISIONS
                            String.valueOf(file.getLocAdded()),               // LOC_ADDED
                            String.valueOf(file.getAvgLocAdded()),            // AVG_LOC_ADDED
                            String.valueOf(file.getNauth()),                  // NUMBER_OF_AUTHORS
                            String.valueOf(file.getMaxLocAdded()),            // MAX_LOC_ADDED
                            String.valueOf(file.getTotalLocRemoved()),        // TOTAL_LOC_REMOVED
                            String.valueOf(file.getMaxLocRemoved()),          // MAX_LOC_REMOVED
                            String.valueOf(file.getAvgLocTouched()),          // AVG_LOC_TOUCHED
                            String.valueOf(file.isBuggy())                    // BUGGY
                    };

                    writer.writeNext(fileData);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Scrive i risultati di WEKA in un singolo file CSV.
     * @param metrics Lista delle metriche calcolate.
     */
    public static void writeWekaResult(List<MetricOfClassifier> metrics) {

        DecimalFormat decimalFormat = new DecimalFormat("#.#####");

        String feature_selection;
        String cost_sensitive;


        //try (CSVWriter writer = new CSVWriter(new FileWriter("fileCSV/wekaResult.csv"))) {
        try (CSVWriter writer = new CSVWriter(new FileWriter("fileCSV/" + metrics.get(0).getNameProject() + "/wekaResult.csv"))) {
            // Intestazione del CSV
            String[] header = { "PROJ", "CLASSIFIER", "ITERATION", "FEATURE_SELECTION", "SAMPLING", "COST_SENSITIVE", "PRECISION", "RECALL", "AUC", "KAPPA", "NPOFB", "TP", "FP", "TN", "FN", "%_OF_TRAINING" };
            writer.writeNext(header);


            // Itera sui risultati dei classificatori per scrivere i dati
            for (MetricOfClassifier metric : metrics) {
                if(metric.isFeature_selection()) {
                    feature_selection = "BEST FIRST";
                } else {
                    feature_selection = "NO";
                }


                if(metric.isCost_sensitive()) {
                    cost_sensitive = "SENSITIVE_LEARNING";
                } else {
                    cost_sensitive = "NO";
                }


                String[] metricData = {
                        metric.getNameProject(),
                        metric.getClassifier(),
                        String.valueOf(metric.getIteration()),
                        feature_selection,
                        metric.getWhatSampling(),
                        cost_sensitive,
                        decimalFormat.format(metric.getPrecision()),
                        decimalFormat.format(metric.getRecall()),
                        decimalFormat.format(metric.getAuc()),
                        decimalFormat.format(metric.getKappa()),
                        decimalFormat.format(metric.getNpofb()),
                        String.valueOf(metric.getTp()),
                        String.valueOf(metric.getFp()),
                        String.valueOf(metric.getTn()),
                        String.valueOf(metric.getFn()),
                        decimalFormat.format(metric.getPercentOfTheTraining())
                };

                writer.writeNext(metricData);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void writeOnAcumeCSV(List<AcumeModel> acumeModelList) {
        try (CSVWriter writer = new CSVWriter(new FileWriter("ACUME/csv/acume.csv"))) {
            // Intestazione del CSV
            String[] header = {"ID", "Size", "Predicted", "Actual"};
            writer.writeNext(header);

            // Itera sui risultati dei classificatori per scrivere i dati
            for (AcumeModel acumeModel : acumeModelList) {
                String[] acumeData = {
                        String.valueOf(acumeModel.getId()),
                        String.valueOf(acumeModel.getSize()),
                        String.valueOf(acumeModel.getProbability()),
                        acumeModel.getValue()
                };

                writer.writeNext(acumeData);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}
