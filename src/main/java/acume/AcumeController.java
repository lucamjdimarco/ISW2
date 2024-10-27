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

import static java.util.logging.Level.SEVERE;
import static utils.WriteCSV.writeOnAcumeCSV;

public class AcumeController {

    private AcumeController() {
        throw new IllegalStateException("AcumeController class");
    }

    public static double retrieveNpofb(Instances data, AbstractClassifier classifier)  {

        double npofb = 0;
        List<AcumeModel> acumeModelList = new ArrayList<>();

        for(int i = 0; i < data.numInstances(); i++) {

            double size = data.get(i).value(0);
            double probability = getProbability(data.get(i), classifier);
            String value = data.get(i).toString(data.numAttributes() - 1).equals("YES") ? "YES" : "NO";

            AcumeModel acumeModel = new AcumeModel(i, size, probability, value);
            acumeModelList.add(acumeModel);
        }

        writeOnAcumeCSV(acumeModelList);

        startAcumeScript();









        return 0;

    }

    private static void startAcumeScript() {
        try{

            String pythonScriptPath = Paths.get("ACUME/main.py").toAbsolutePath().toString();

            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command("python", pythonScriptPath);
            //processBuilder.directory();
            Process process = processBuilder.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while((line = reader.readLine()) != null) {
                System.out.println(line);
            }

            reader.close();

            BufferedReader err = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            while ((line = err.readLine()) != null) {
                //logger.log(SEVERE,line);
                System.out.println(line);
            }

            err.close();



            int exitCode = process.waitFor();
            System.out.println("\nExited with error code : " + exitCode);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static double getProbability(Instance instance, AbstractClassifier classifier) {
        try {
            double[] predicted = classifier.distributionForInstance(instance);

            for(int i = 0; i < predicted.length; i++) {
                if(instance.classAttribute().value(i).equals("YES")) {
                    return predicted[i];
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
        return 0;
    }


}
