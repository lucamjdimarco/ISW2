package acume;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import model.AcumeModel;
import weka.classifiers.AbstractClassifier;
import weka.core.Instance;
import weka.core.Instances;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.SEVERE;
import static utils.WriteCSV.writeOnAcumeCSV;
import java.util.logging.Logger;

public class AcumeController {

    private static final Logger logger = Logger.getLogger(AcumeController.class.getName());

    private AcumeController() {
        throw new IllegalStateException("AcumeController class");
    }

    public static double retrieveNpofb(Instances data, AbstractClassifier classifier) {

        try {
            List<AcumeModel> acumeModelList = new ArrayList<>();
            int lastAttributesIndex = data.classIndex();
            double npofb20 = -1;

            for(int i = 0; i < data.numInstances(); i++) {
                Instance instance = data.instance(i);

                double size = instance.value(0);
                double probability = getProbability(instance, classifier);

                if (probability < 0) {
                    logger.log(SEVERE, "Probabilità non valida per l'istanza");
                    continue;
                }

                String actual = instance.stringValue(lastAttributesIndex);

                AcumeModel acumeModel = new AcumeModel(i, size, probability, actual);
                acumeModelList.add(acumeModel);
            }

            writeOnAcumeCSV(acumeModelList);
            startAcumeScript();

            npofb20 = getNPofB20Value(Paths.get("EAM_NEAM_output.csv").toAbsolutePath().toString());

            return npofb20;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.log(SEVERE, e.getMessage());
            return 0;
        }  catch (Exception e) {
            logger.log(SEVERE, e.getMessage());
            return 0;
        }
    }

    public static double getNPofB20Value(String filePath) {
        try (CSVReader csvReader = new CSVReader(new FileReader(filePath))) {
            List<String[]> allRows = csvReader.readAll();
            String[] header = allRows.get(0);

            int columnIndex = -1;
            for (int i = 0; i < header.length; i++) {
                if ("Npofb20".equalsIgnoreCase(header[i].trim())) {
                    columnIndex = i;
                    break;
                }
            }

            if (columnIndex == -1) {
                return 0;
            }

            String valueStr = allRows.get(1)[columnIndex];

            return Double.parseDouble(valueStr);
        } catch (IOException | CsvException e) {
            logger.log(SEVERE, e.getMessage());
            e.printStackTrace();
        } catch (NumberFormatException e) {
            logger.log(SEVERE, e.getMessage());
        }
        return 0;
    }

    private static void startAcumeScript() throws InterruptedException {
        try {
            String pythonScriptPath = Paths.get("ACUME/main.py").toAbsolutePath().toString();
            String[] cmd = {"python3", pythonScriptPath, "NPofB"};
            ProcessBuilder processBuilder = new ProcessBuilder(cmd);

            Process process = processBuilder.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                 BufferedReader err = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {

                reader.lines().forEach(line -> logger.log(INFO, line));
                err.lines().forEach(line -> logger.log(SEVERE, line));
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                logger.log(INFO, "Script Acume eseguito correttamente");
            } else {
                logger.log(SEVERE, "Script Acume fallito");
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } catch (Exception e) {
            logger.log(SEVERE, e.getMessage());
        }
    }

    private static double getProbability(Instance instance, AbstractClassifier classifier) throws Exception {
        int isBugIndex = instance.classAttribute().indexOfValue("YES");
        double[] distribution = classifier.distributionForInstance(instance);

        if (isBugIndex == -1) {
            logger.log(SEVERE, "Valore 'YES' non trovato tra le classi disponibili");
            return -1;
        }

        return distribution[isBugIndex];
    }
}
