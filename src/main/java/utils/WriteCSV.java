package utils;

import com.opencsv.CSVWriter;
import model.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.text.DecimalFormat;
import java.util.logging.Level;
import java.util.logging.Logger;


public class WriteCSV {

    private static final Logger logger = Logger.getLogger(WriteCSV.class.getName());

    private WriteCSV() {
        throw new IllegalStateException("Utility class");
    }
    public static void writeReleasesForWalkForward(List<Release> releases, List<Ticket> tickets, String baseCsvFilePathForTraining, String baseCsvFilePathForTesting, String repo) {

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

            String[] header = { "VERSION", "FILE_NAME", "LOC", "LOC_TOUCHED", "NUMBER_OF_REVISIONS",
                    "LOC_ADDED", "AVG_LOC_ADDED", "NUMBER_OF_AUTHORS", "MAX_LOC_ADDED",
                    "TOTAL_LOC_REMOVED", "MAX_LOC_REMOVED", "AVG_LOC_TOUCHED", "BUGGY" };
            writer.writeNext(header);

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

        String featureselection;
        String costsensitive;


        try (CSVWriter writer = new CSVWriter(new FileWriter("fileCSV/" + metrics.get(0).getNameProject() + "/wekaResult.csv"))) {
            String[] header = { "PROJ", "CLASSIFIER", "ITERATION", "FEATURE_SELECTION", "SAMPLING", "COST_SENSITIVE", "PRECISION", "RECALL", "AUC", "KAPPA", "NPOFB", "TP", "FP", "TN", "FN", "%_OF_TRAINING"};
            writer.writeNext(header);

            for (MetricOfClassifier metric : metrics) {
                if(metric.isFeatureSelection()) {
                    featureselection = "BEST FIRST";
                } else {
                    featureselection = "NO";
                }


                if(metric.isCostSensitive()) {
                    costsensitive = "SENSITIVE_LEARNING";
                } else {
                    costsensitive = "NO";
                }


                String[] metricData = {
                        metric.getNameProject(),
                        metric.getClassifier(),
                        String.valueOf(metric.getIteration()),
                        featureselection,
                        metric.getWhatSampling(),
                        costsensitive,
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

        File subfolder = new File("csv");
        if (!subfolder.mkdirs() && !subfolder.exists()) {
            logger.log(Level.SEVERE, "Errore nella creazione della cartella di output");
        }

        String outName = subfolder + File.separator + "acume.csv";
        try (FileWriter fileWriter = new FileWriter(outName)) {
            fileWriter.append("ID,Size,Prob,Actual\n");  // Header without quotes
            for (AcumeModel acumeEntry : acumeModelList) {
                // Append values directly without additional quotes
                fileWriter.append(String.valueOf(acumeEntry.getId())).append(",")
                        .append(String.valueOf(acumeEntry.getSize())).append(",")
                        .append(String.valueOf(acumeEntry.getProbability())).append(",")
                        .append(acumeEntry.getValue()).append("\n");
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, e.getMessage());
        }
    }

    private static boolean isValidDirectory(File directory) {
        return directory.exists() && directory.isDirectory();
    }

    private static void deleteFile(File file) {
        if (file.isFile() && !file.delete()) {
            logger.log(Level.SEVERE, () -> String.format("Impossibile eliminare il file: %s", file.getAbsolutePath()));
        }
    }


    public static void cleanDirectory(String directoryPath) {
        File directory = new File(directoryPath);

        if (!isValidDirectory(directory)) {
            logger.log(Level.SEVERE, () -> String.format("La directory non esiste o non è una directory: %s", directoryPath));
            return;
        }

        File[] files = directory.listFiles();
        if (files == null) return;

        for (File file : files) {
            deleteFile(file);
        }
    }






}
