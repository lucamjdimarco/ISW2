package weka;

import weka.core.converters.CSVLoader;
import weka.core.converters.ArffSaver;
import weka.core.Instances;

import java.io.File;

public class WekaController {

    public static void convertCSVtoARFF(String csvFilePath, String arffFilePath) throws Exception {

        CSVLoader loader = new CSVLoader();
        loader.setSource(new File(csvFilePath));
        Instances data = loader.getDataSet();


        ArffSaver saver = new ArffSaver();
        saver.setInstances(data);
        saver.setFile(new File(arffFilePath));
        saver.writeBatch();
    }


}
