package acume;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
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

                // Recupera i valori di Size e probabilità di essere buggy
                double size = instance.value(0);
                double probability = getProbability(instance, classifier);

                // Gestione del caso in cui la probabilità non sia valida
                if (probability < 0) {
                    logger.log(SEVERE, "Probabilità non valida per l'istanza " + i);
                    continue; // Salta l'istanza in caso di errore
                }

                // Valore effettivo: YES o NO
                String actual = instance.stringValue(lastAttributesIndex);

                // Crea e aggiungi l'istanza del modello
                AcumeModel acumeModel = new AcumeModel(i, size, probability, actual);
                acumeModelList.add(acumeModel);
            }

            // Scrive i risultati sul file CSV
            writeOnAcumeCSV(acumeModelList);

            // Avvia lo script Python per calcolare NPofB
            startAcumeScript();

            npofb20 = getNPofB20Value(Paths.get("EAM_NEAM_output.csv").toAbsolutePath().toString());

            return npofb20;

        } catch (Exception e) {
            logger.log(SEVERE, "Errore durante il calcolo di NPofB", e);
            return 0;
        }
    }

    public static double getNPofB20Value(String filePath) {
        try (CSVReader csvReader = new CSVReader(new FileReader(filePath))) {
            List<String[]> allRows = csvReader.readAll();
            String[] header = allRows.get(0);

            //trova l'indice della colonna NPofB20
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
            logger.log(SEVERE, "Errore durante la lettura del file CSV", e);
            e.printStackTrace();
        } catch (NumberFormatException e) {
            logger.log(SEVERE, "Valore non valido in NPofB20: " + e.getMessage());
        }
        return 0; // In caso di errore
    }

    private static void startAcumeScript() {
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
                logger.log(SEVERE, "Script Acume fallito con codice di uscita " + exitCode);
            }

        } catch (Exception e) {
            logger.log(SEVERE, "Errore durante l'esecuzione dello script Acume", e);
        }
    }

    private static double getProbability(Instance instance, AbstractClassifier classifier) throws Exception {
        int isBugIndex = instance.classAttribute().indexOfValue("YES");

        /*System.out.println("Class attribute index: " + instance.classAttribute().index());
        System.out.println("YES index: " + instance.classAttribute().indexOfValue("YES"));
        System.out.println("NO index: " + instance.classAttribute().indexOfValue("NO"));*/





        if (isBugIndex == -1) {
            logger.log(SEVERE, "Valore 'YES' non trovato tra le classi disponibili");
            return -1; // Indica un errore
        }

        double[] distribution = classifier.distributionForInstance(instance);

        //System.out.println("Distribution: " + Arrays.toString(distribution));

        return distribution[isBugIndex];
    }
}
