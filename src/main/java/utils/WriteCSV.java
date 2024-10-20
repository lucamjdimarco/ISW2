package utils;

import com.opencsv.CSVWriter;
import model.FileJava;
import model.Release;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;


public class WriteCSV {

    public static void writeReleasesToCsv(List<Release> releases, String csvFilePath) {
        try (CSVWriter writer = new CSVWriter(new FileWriter(csvFilePath))) {
            String[] header = { "VERSION" , "FILE_NAME", "LOC", "LOC_TOUCHED", "NUMBER_OF_REVISIONS", "LOC_ADDED", "AVG_LOC_ADDED", "NUMBER_OF_AUTHORS", "MAX_LOC_ADDED", "TOTAL_LOC_REMOVED", "MAX_LOC_REMOVED", "AVG_LOC_TOUCHED", "BUGGY" };
            writer.writeNext(header);

            for (Release release : releases) {
                for (FileJava file : release.getFiles()) {
                    String[] fileData = {
                            String.valueOf(release.getIndex()),
                            file.getName(),
                            String.valueOf(file.getLoc()),
                            String.valueOf(file.getLocTouched()),
                            String.valueOf(file.getNr()),
                            String.valueOf(file.getLocAdded()),
                            String.valueOf(file.getAvgLocAdded()),
                            String.valueOf(file.getNauth()),
                            String.valueOf(file.getMaxLocAdded()),
                            String.valueOf(file.getTotalLocRemoved()),
                            String.valueOf(file.getMaxLocRemoved()),
                            String.valueOf(file.getAvgLocTouched()),
                            file.isBuggy()
                    };

                    writer.writeNext(fileData);


                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}
