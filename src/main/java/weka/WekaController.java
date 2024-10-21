package weka;

import weka.core.Instances;
import weka.core.converters.ArffSaver;
import weka.core.converters.CSVLoader;

import java.io.File;

public class WekaController {

    private WekaController() {
        throw new IllegalStateException("Utility class");
    }

    public static void convertCSVtoARFF(String csvFile, String arffFile) {
        /*try {
            String pythonScriptPath = Paths.get("convert.py").toAbsolutePath().toString();

            ProcessBuilder pb = new ProcessBuilder("python", pythonScriptPath);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }

            int exitCode = process.waitFor();
            System.out.println("Exit Code: " + exitCode);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }*/

        try {
            CSVLoader loader = new CSVLoader();
            loader.setSource(new File(csvFile));
            Instances data = loader.getDataSet();

            data.deleteAttributeAt(1);
            data.deleteAttributeAt(0);

            // save ARFF
            ArffSaver saver = new ArffSaver();
            saver.setInstances(data);
            saver.setFile(new File(arffFile));
            saver.setDestination(new File(arffFile));
            saver.writeBatch();
        } catch (Exception e) {
            e.printStackTrace();
        }



    }

    public static void convertAllCsvInFolder(String folderPath) {
        File folder = new File(folderPath);
        for (File file : folder.listFiles()) {
            if (file.getName().endsWith(".csv")) {
                String csvFile = file.getAbsolutePath();
                String arffFile = csvFile.substring(0, csvFile.length() - 4) + ".arff";
                convertCSVtoARFF(csvFile, arffFile);
            }
        }
    }


}
