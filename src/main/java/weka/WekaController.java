package weka;

import model.MetricOfClassifier;
import utils.WriteCSV;
import weka.classifiers.AbstractClassifier;
import weka.classifiers.Classifier;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.lazy.IBk;
import weka.classifiers.meta.FilteredClassifier;
import weka.classifiers.trees.RandomForest;
import weka.classifiers.evaluation.Evaluation;
import weka.core.Instances;
import weka.core.Utils;
import weka.core.converters.ArffSaver;
import weka.core.converters.CSVLoader;
import weka.classifiers.CostMatrix;
import weka.classifiers.meta.CostSensitiveClassifier;

import weka.attributeSelection.BestFirst;
import weka.attributeSelection.CfsSubsetEval;

import weka.filters.Filter;
import weka.filters.supervised.attribute.AttributeSelection;

import weka.core.converters.ConverterUtils;
import weka.filters.supervised.instance.Resample;
import weka.filters.supervised.instance.SpreadSubsample;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static acume.AcumeController.retrieveNpofb;

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

            Path path = Paths.get(arffFile);
            List<String> lines = Files.readAllLines(path);
            for (String line : lines) {
                if (line.contains("@attribute BUGGY")) {
                    lines.set(lines.indexOf(line), "@attribute BUGGY {YES,NO}");
                }
            }
            Files.write(path, lines, StandardCharsets.UTF_8);


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

    public static void calculateWeka(String nameProj, int numReleases) {

        List<MetricOfClassifier> metricOfClassifierList = new ArrayList<>();

        Classifier[] classifiers = new Classifier[]{
                new RandomForest(),
                new NaiveBayes(),
                new IBk()
        };

        try {
            for (int walkIteration = 1; walkIteration <= numReleases - 1; walkIteration++) {

                String path1 = "fileCSV/" + nameProj + "/training/";
                String path2 = "fileCSV/" + nameProj + "/testing/";
                String trainingFilePath = Paths.get(path1, "file_train_step_" + walkIteration + ".arff").toAbsolutePath().toString();
                String testingFilePath = Paths.get(path2, "file_test_step_" + walkIteration + ".arff").toAbsolutePath().toString();

                ConverterUtils.DataSource trainingSource = new ConverterUtils.DataSource(trainingFilePath);
                ConverterUtils.DataSource testingSource = new ConverterUtils.DataSource(testingFilePath);

                Instances trainingData = trainingSource.getDataSet();
                Instances testingData = testingSource.getDataSet();

                trainingData.setClassIndex(trainingData.numAttributes() - 1);
                testingData.setClassIndex(testingData.numAttributes() - 1);

                // ---- RUN SENZA SELECTION - SEMPLICE ----
                runSimpleClassifier(nameProj, walkIteration, trainingData, testingData, metricOfClassifierList, classifiers);

                // ---- RUN CON FUTURE SELECTION (BEST FIRST) SENZA SAMPLING ----
                runWithFeatureSelection(nameProj, walkIteration, trainingData, testingData, metricOfClassifierList, classifiers, false, false);

                // ---- RUN CON FUTURE SELECTION E UNDER-SAMPLING ----
                runWithFeatureSelectionAndUnderSampling(nameProj, walkIteration, trainingData, testingData, metricOfClassifierList, classifiers);

                // ---- RUN CON FUTURE SELECTION E OVER-SAMPLING ----
                runWithFeatureSelectionAndOverSampling(nameProj, walkIteration, trainingData, testingData, metricOfClassifierList, classifiers);

                // ---- RUN CON FUTURE SELECTION E COST SENSITIVE ----
                runFeatureSelectionWithCostSensitive(nameProj, walkIteration, trainingData, testingData, metricOfClassifierList, classifiers);



            }

            WriteCSV.writeWekaResult(metricOfClassifierList);

        } catch (Exception e) {
            e.printStackTrace();
        }


    }

    private static void runSimpleClassifier(String nameProj, int walkIteration, Instances trainingData, Instances testingData,
                                            List<MetricOfClassifier> metricOfClassifierList, Classifier[] classifiers) throws Exception {
        // ---- RUN SENZA SELECTION - SEMPLICE ----

        for (Classifier classifier : classifiers) {

            classifier.buildClassifier(trainingData);
            Evaluation eval = new Evaluation(testingData);
            eval.evaluateModel(classifier, testingData);


            MetricOfClassifier classifierEval = new MetricOfClassifier(nameProj, walkIteration,
                    classifier.getClass().getSimpleName(), false, false, false);

            setValueinTheClassifier(classifierEval, eval, trainingData.numInstances(), testingData.numInstances(), testingData, (AbstractClassifier) classifier);
            metricOfClassifierList.add(classifierEval);
        }
    }

    /*public static void printInstances(Instances instances) {
        for (int i = 0; i < instances.numInstances(); i++) {
            // Stampa l'istanza corrente
            System.out.println(instances.instance(i).toString());
        }
    }*/


    private static void runWithFeatureSelection(String nameProj, int walkIteration, Instances trainingData, Instances testingData,
                                                List<MetricOfClassifier> metricOfClassifierList, Classifier[] classifiers, boolean isUnderSampling, boolean isOverSampling) throws Exception {

        // ---- FUTURE SELECTION ----
        AttributeSelection attributeSelection = new AttributeSelection();
        CfsSubsetEval eval = new CfsSubsetEval();
        BestFirst search = new BestFirst();

        search.setOptions(Utils.splitOptions("-D 2"));

        attributeSelection.setEvaluator(eval);
        attributeSelection.setSearch(search);
        attributeSelection.setInputFormat(trainingData);

        Instances filteredTrainingData = Filter.useFilter(trainingData, attributeSelection);
        Instances filteredTestingData = Filter.useFilter(testingData, attributeSelection);

        /*System.out.println("Filtered Training Data:");
        printInstances(filteredTrainingData);

        System.out.println("Filtered Testing Data:");
        printInstances(filteredTestingData);*/

        filteredTrainingData.setClassIndex(filteredTrainingData.numAttributes() - 1);
        filteredTestingData.setClassIndex(filteredTestingData.numAttributes() - 1);


        for (Classifier classifier : classifiers) {
            classifier.buildClassifier(filteredTrainingData);
            //Evaluation evalModel = new Evaluation(filteredTestingData);
            Evaluation evalModel = new Evaluation(testingData);
            evalModel.evaluateModel(classifier, filteredTestingData);

            MetricOfClassifier classifierEval = new MetricOfClassifier(nameProj, walkIteration,
                    classifier.getClass().getSimpleName(), true, false, false);
            setValueinTheClassifier(classifierEval, evalModel, filteredTrainingData.numInstances(), filteredTestingData.numInstances(), filteredTestingData, (AbstractClassifier) classifier);
            metricOfClassifierList.add(classifierEval);
        }
    }

    private static void runWithFeatureSelectionAndUnderSampling(String nameProj, int walkIteration, Instances trainingData, Instances testingData,
                                                                List<MetricOfClassifier> metricOfClassifierList, Classifier[] classifiers) throws Exception {

        // ---- FUTURE SELECTION ----
        AttributeSelection attributeSelection = new AttributeSelection();
        CfsSubsetEval eval = new CfsSubsetEval();
        BestFirst search = new BestFirst();

        search.setOptions(Utils.splitOptions("-D 2"));

        attributeSelection.setEvaluator(eval);
        attributeSelection.setSearch(search);
        attributeSelection.setInputFormat(trainingData);

        // applico il filtro al training set
        Instances filteredTrainingData = Filter.useFilter(trainingData, attributeSelection);
        Instances filteredTestingData = Filter.useFilter(testingData, attributeSelection);

        filteredTrainingData.setClassIndex(filteredTrainingData.numAttributes() - 1);
        filteredTestingData.setClassIndex(filteredTestingData.numAttributes() - 1);

        // ---- UNDER-SAMPLING ----
        SpreadSubsample underSampler = new SpreadSubsample();
        underSampler.setInputFormat(filteredTrainingData);
        // -M 1.0 = undersampling 1:1 --> il filtro rimuoverà abbastanza istanze della classe maggioritaria per mantenere un rapporto di 1:1
        //Bilanciamento classi di maggioranza
        underSampler.setOptions(Utils.splitOptions("-M 1.0"));


        // ---- RUN CON I DATI UNDER-SAMPLED ----
        for (Classifier classifier : classifiers) {
            FilteredClassifier fc = new FilteredClassifier();
            fc.setFilter(underSampler);
            fc.setClassifier(classifier);
            fc.buildClassifier(filteredTrainingData);
            //Evaluation evalModel = new Evaluation(filteredTestingData);
            Evaluation evalModel = new Evaluation(testingData);
            evalModel.evaluateModel(fc, filteredTestingData);

            MetricOfClassifier classifierEval = new MetricOfClassifier(nameProj, walkIteration,
                    classifier.getClass().getSimpleName(), true, true, false);
            setValueinTheClassifier(classifierEval, evalModel, filteredTrainingData.numInstances(), filteredTestingData.numInstances(), filteredTestingData, (AbstractClassifier) classifier);
            classifierEval.setWhatSampling("UNDERSAMPLING");
            metricOfClassifierList.add(classifierEval);
        }
    }

    private static double calculateMajorityClassPercentage(Instances data) {

        int[] classCounts = new int[data.numClasses()];

        for (int i = 0; i < data.numInstances(); i++) {
            int classIndex = (int) data.instance(i).classValue();
            classCounts[classIndex]++;
        }

        int majorityCount = 0;
        for (int count : classCounts) {
            if (count > majorityCount) {
                majorityCount = count;
            }
        }
        double majorityClassPercentage = (double) majorityCount / data.numInstances() * 100.0;

        return majorityClassPercentage;
    }


    private static void runWithFeatureSelectionAndOverSampling(String nameProj, int walkIteration, Instances trainingData, Instances testingData,
                                                               List<MetricOfClassifier> metricOfClassifierList, Classifier[] classifiers) throws Exception {

        // ---- FUTURE SELECTION ----
        AttributeSelection attributeSelection = new AttributeSelection();
        CfsSubsetEval eval = new CfsSubsetEval();
        BestFirst search = new BestFirst();

        search.setOptions(Utils.splitOptions("-D 2"));

        attributeSelection.setEvaluator(eval);
        attributeSelection.setSearch(search);
        attributeSelection.setInputFormat(trainingData);

        Instances filteredTrainingData = Filter.useFilter(trainingData, attributeSelection);
        Instances filteredTestingData = Filter.useFilter(testingData, attributeSelection);

        filteredTrainingData.setClassIndex(filteredTrainingData.numAttributes() - 1);
        filteredTestingData.setClassIndex(filteredTestingData.numAttributes() - 1);


        // ---- OVER-SAMPLING ----
        Resample overSampler = new Resample();
        overSampler.setInputFormat(filteredTrainingData);
        double majorityClassPercentage = calculateMajorityClassPercentage(filteredTrainingData);
        // -B 1.0 = oversampling 1:1 --> il filtro aggiungerà abbastanza istanze della classe minoritaria per mantenere un rapporto di 1:1
        double sampleSizePercent = majorityClassPercentage * 2;
        overSampler.setOptions(Utils.splitOptions("-B 1.0 -Z " + sampleSizePercent));

        for (Classifier classifier : classifiers) {
            FilteredClassifier fc = new FilteredClassifier();
            fc.setFilter(overSampler);
            fc.setClassifier(classifier);
            fc.buildClassifier(filteredTrainingData);

            //Evaluation evalModel = new Evaluation(filteredTestingData);
            Evaluation evalModel = new Evaluation(testingData);
            evalModel.evaluateModel(fc, filteredTestingData);

            MetricOfClassifier classifierEval = new MetricOfClassifier(nameProj, walkIteration,
                    classifier.getClass().getSimpleName(), true, true, false);
            setValueinTheClassifier(classifierEval, evalModel, filteredTrainingData.numInstances(), filteredTestingData.numInstances(), filteredTestingData, (AbstractClassifier) classifier);
            classifierEval.setWhatSampling("OVERSAMPLING");
            metricOfClassifierList.add(classifierEval);
        }
    }

    //COST SENSITIVE CON FUTURE SELECTION
    private static void runFeatureSelectionWithCostSensitive(String nameProj, int walkIteration, Instances trainingData, Instances testingData,
                                                             List<MetricOfClassifier> metricOfClassifierList, Classifier[] classifiers) throws Exception {


        // ---- FEATURE SELECTION  ----
        AttributeSelection attributeSelection = new AttributeSelection();
        CfsSubsetEval eval = new CfsSubsetEval();
        BestFirst search = new BestFirst();

        //BEST FIRST BI-DIREZIONALE (se non specifico è unidirezionale)
        search.setOptions(Utils.splitOptions("-D 2"));

        attributeSelection.setEvaluator(eval);
        attributeSelection.setSearch(search);
        attributeSelection.setInputFormat(trainingData);

        Instances filteredTrainingData = Filter.useFilter(trainingData, attributeSelection);
        Instances filteredTestingData = Filter.useFilter(testingData, attributeSelection);

        filteredTrainingData.setClassIndex(filteredTrainingData.numAttributes() - 1);
        filteredTestingData.setClassIndex(filteredTestingData.numAttributes() - 1);

        //Imposto la matrice dei costi --> CFN = 10 * CFP
        CostMatrix costMatrix = new CostMatrix(2);
        costMatrix.setCell(0, 0, 0.0);
        costMatrix.setCell(0, 1, 10.0);
        costMatrix.setCell(1, 0, 1.0);
        costMatrix.setCell(1, 1, 0.0);

        for (Classifier baseClassifier : classifiers) {
            // Configura il classificatore cost-sensitive
            CostSensitiveClassifier costSensitiveClassifier = new CostSensitiveClassifier();
            costSensitiveClassifier.setClassifier(baseClassifier);
            costSensitiveClassifier.setCostMatrix(costMatrix);
            //costSensitiveClassifier.setMinimizeExpectedCost(true);

            costSensitiveClassifier.buildClassifier(filteredTrainingData);

            //Evaluation evalModel = new Evaluation(filteredTestingData, costMatrix);
            Evaluation evalModel = new Evaluation(testingData, costMatrix);
            evalModel.evaluateModel(costSensitiveClassifier, filteredTestingData);

            MetricOfClassifier classifierEval = new MetricOfClassifier(nameProj, walkIteration,
                    baseClassifier.getClass().getSimpleName(), true, false, true);

            setValueinTheClassifier(classifierEval, evalModel, filteredTrainingData.numInstances(), filteredTestingData.numInstances(), filteredTestingData, (AbstractClassifier) baseClassifier);


            metricOfClassifierList.add(classifierEval);
        }
    }



    private static void setValueinTheClassifier(MetricOfClassifier classifier, Evaluation eval, int trainingSet, int testingSet, Instances data, AbstractClassifier cls) {

        classifier.setPrecision(eval.precision(0));
        classifier.setRecall(eval.recall(0));
        classifier.setAuc(eval.areaUnderROC(0));
        classifier.setKappa(eval.kappa());
        classifier.setTp(eval.numTruePositives(0));
        classifier.setFp(eval.numFalsePositives(0));
        classifier.setTn(eval.numTrueNegatives(0));
        classifier.setFn(eval.numFalseNegatives(0));
        classifier.setPercentOfTheTraining(100.0 * trainingSet / (trainingSet + testingSet));

        classifier.setNpofb(retrieveNpofb(data, cls));

    }





}
