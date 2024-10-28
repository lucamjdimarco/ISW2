package acume;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

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

    public static double retrieveNpofb(Instances data, AbstractClassifier classifier)  {

        try {

            //double npofb = 0;
            List<AcumeModel> acumeModelList = new ArrayList<>();
            int lastAttributesIndex = data.numAttributes() - 1;

            for(int i = 0; i < data.numInstances(); i++) {

                double size = data.get(i).value(0);
                double probability = getProbability(data.get(i), classifier);
                String value = data.get(i).toString(lastAttributesIndex).equals("YES") ? "YES" : "NO";

                AcumeModel acumeModel = new AcumeModel(i, size, probability, value);
                acumeModelList.add(acumeModel);
            }

            writeOnAcumeCSV(acumeModelList);

            startAcumeScript();


            return 0;

        } catch (Exception e) {
            logger.log(SEVERE,"Error while retrieving Npofb");
            e.printStackTrace();
            return 0;
        }


    }

    private static void startAcumeScript() {
        try{

            String pythonScriptPath = Paths.get("ACUME/main.py").toAbsolutePath().toString();

            String[] cmd = {"python3", pythonScriptPath, "NPofB"};
            ProcessBuilder processBuilder = new ProcessBuilder(cmd);

            //processBuilder.command("python", pythonScriptPath, "NPofB");
            //processBuilder.directory();
            Process process = processBuilder.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while((line = reader.readLine()) != null) {
                logger.log(INFO,line);
            }

            reader.close();

            BufferedReader err = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            while ((line = err.readLine()) != null) {
                logger.log(SEVERE,line);
            }

            err.close();

            int exitCode = process.waitFor();
            if(exitCode == 0) {
                logger.log(INFO,"Acume script executed successfully");
            } else {
                logger.log(SEVERE,"Acume script failed");
            }

        } catch (Exception e) {
            logger.log(SEVERE,"Error while executing Acume script");
            e.printStackTrace();
        }
    }

    private static double getProbability(Instance instance, AbstractClassifier classifier) throws Exception {

        double[] predicted = classifier.distributionForInstance(instance);

        for(int i = 0; i < predicted.length; i++) {
            if(instance.classAttribute().value(i).equals("YES")) {
                return predicted[i];
            }
        }
        throw new Exception("Error while getting probability");
    }


}
