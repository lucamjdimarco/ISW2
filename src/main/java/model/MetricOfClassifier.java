package model;

public class MetricOfClassifier {

    private String nameProject;
    private String classifier;
    //index dell'iterazione della walk forward
    private int iteration;
    private boolean featureselection;
    private boolean sampling;
    private boolean costsensitive;
    private double precision;
    private double recall;
    private double auc;
    private double kappa;
    private double tp;
    private double fp;
    private double tn;
    private double fn;

    private double npofb;

    private double percentOfTheTraining;

    private String whatSampling;


    public MetricOfClassifier(String nameProject, int iteration, String classifier, boolean featureselection, boolean sampling, boolean costsensitive) {
        this.nameProject = nameProject;
        this.iteration = iteration;
        this.classifier = classifier;
        this.featureselection = featureselection;
        this.sampling = sampling;
        this.costsensitive = costsensitive;
        this.precision = 0;
        this.recall = 0;
        this.auc = 0;
        this.kappa = 0;
        this.tp = 0;
        this.fp = 0;
        this.tn = 0;
        this.fn = 0;
        this.percentOfTheTraining = 0;
        this.npofb = 0;
        this.whatSampling = "NO";

    }

    /* -- GETTER -- */

    public String getNameProject() {
        return nameProject;
    }

    public String getClassifier() {
        return classifier;
    }

    public int getIteration() {
        return iteration;
    }

    public boolean isFeatureSelection() {
        return featureselection;
    }

    public boolean isSampling() {
        return sampling;
    }

    public boolean isCostSensitive() {
        return costsensitive;
    }

    public double getPrecision() {
        return precision;
    }

    public double getRecall() {
        return recall;
    }

    public double getAuc() {
        return auc;
    }

    public double getKappa() {
        return kappa;
    }

    public double getTp() {
        return tp;
    }

    public double getFp() {
        return fp;
    }

    public double getTn() {
        return tn;
    }

    public double getFn() {
        return fn;
    }

    public double getPercentOfTheTraining() {
        return percentOfTheTraining;
    }

    public double getNpofb() {
        return npofb;
    }

    public String getWhatSampling() {
        return whatSampling;
    }


    /* -- SETTER -- */

    public void setNameProject(String nameProject) {
        this.nameProject = nameProject;
    }

    public void setClassifier(String classifier) {
        this.classifier = classifier;
    }

    public void setIteration(int iteration) {
        this.iteration = iteration;
    }

    public void setFeatureSelection(boolean featureselection) {
        this.featureselection = featureselection;
    }

    public void setSampling(boolean sampling) {
        this.sampling = sampling;
    }

    public void setCostSensitive(boolean costsensitive) {
        this.costsensitive = costsensitive;
    }

    public void setPrecision(double precision) {
        this.precision = precision;
    }

    public void setRecall(double recall) {
        this.recall = recall;
    }

    public void setAuc(double auc) {
        this.auc = auc;
    }

    public void setKappa(double kappa) {
        this.kappa = kappa;
    }

    public void setTp(double tp) {
        this.tp = tp;
    }

    public void setFp(double fp) {
        this.fp = fp;
    }

    public void setTn(double tn) {
        this.tn = tn;
    }

    public void setFn(double fn) {
        this.fn = fn;
    }

    public void setPercentOfTheTraining(double percentOfTheTraining) {
        this.percentOfTheTraining = percentOfTheTraining;
    }

    public void setWhatSampling(String whatSampling) {
        this.whatSampling = whatSampling;
    }
    public void setNpofb(double npofb) {
        this.npofb = npofb;
    }


    @Override
    public String toString() {
        return "MetricOfClassifier{" +
                "nameProject='" + nameProject + '\'' +
                ", classifier='" + classifier + '\'' +
                ", iteration=" + iteration +
                ", feature_selection=" + featureselection +
                ", sampling=" + sampling +
                ", cost_sensitive=" + costsensitive +
                ", precision=" + precision +
                ", recall=" + recall +
                ", auc=" + auc +
                ", kappa=" + kappa +
                ", npofb=" + npofb +
                ", tp=" + tp +
                ", fp=" + fp +
                ", tn=" + tn +
                ", fn=" + fn +
                ", percentOfTheTraining=" + percentOfTheTraining +
                '}';
    }
}
