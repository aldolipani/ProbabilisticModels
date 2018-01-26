package at.ac.tuwien.ifs;

import org.terrier.matching.models.WeightingModel;
import org.terrier.matching.models.WeightingModelLibrary;
import org.terrier.structures.DocumentIndex;
import org.terrier.structures.DocumentIndexEntry;
import org.terrier.structures.Index;
import org.terrier.structures.postings.Posting;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;

/**
 * This class implements the TF_IDF weighting model with verboseness and 4 TF Quantifications,
 * and with Exposed ParameterS, presented by Lipani et al. in the paper "*".
 *
 * @author Aldo Lipani
 */
public class TFs_IDF_EPs extends WeightingModel {

    private static final long serialVersionUID = 1L;

    private static Index index;

    /**
     * tf quantification
     **/
    private final String tfQuantification;

    static final public String[] tfQuantifications = new String[]{"total", "log", "bm25", "constant"};

    /**
     * tf normalization combination
     **/
    private final String tfNormalizationCombination;

    static final public String[] tfNormalizationCombinations = new String[]{"linear", "product"};

    /**
     * tf normalization pivotization
     **/
    private String tfNormalizationPivotization;

    //static final public String[] tfNormalizationPivotizations = new String[]{"non_elite", "elite"};
    static final public String[] tfNormalizationPivotizations = new String[]{"non_elite"};

    /**
     * model name
     */
    private static final String name = "TFs_IDF_EPs";

    /**
     * The constant k_1.
     */
    private final double k_1;

    /**
     * The constant bs.
     */
    private final double b;
    private final double a;

    public static double avgV = -1d;
    public static int nZnD = -1;

    /**
     * A default constructor to make this model.
     */
    public TFs_IDF_EPs() {
        super();
        this.tfQuantification = System.getProperty("tf.quantification", "total").toLowerCase();
        this.tfNormalizationCombination = System.getProperty("tf.normalization.combination", "linear").toLowerCase();
        this.tfNormalizationPivotization = System.getProperty("tf.normalization.pivotization", "non_elite").toLowerCase();
        this.b = Double.parseDouble(System.getProperty("b", "0.5d"));
        this.a = Double.parseDouble(System.getProperty("a", "0.5d"));
        this.k_1 = Double.parseDouble(System.getProperty("k1", "1.2d"));
    }

    public TFs_IDF_EPs(String tfQuantification,
                       String tfNormalizationCombination,
                       String tfNormalizationPivotization,
                       double b,
                       double a,
                       double k_1){
        this.tfQuantification = tfQuantification;
        this.tfNormalizationCombination = tfNormalizationCombination;
        this.tfNormalizationPivotization = tfNormalizationPivotization;
        this.b = b;
        this.a = a;
        this.k_1 = k_1;
    }

    /**
     * Returns the name of the model, in this case "TFs_IDF"
     *
     * @return the name of the model
     */
    public final String getInfo() {
        return name +
                ".q_" + tfQuantification +
                ".nc_" + tfNormalizationCombination +
                ".np_" + tfNormalizationPivotization +
                ".k1_" + String.format("%.4f", k_1) +
                ".b_" + String.format("%.1f", b) +
                ".a_" + String.format("%.1f", a);
    }

    @Override
    public double score(Posting p) {
        int docId = p.getId();
        double tfd = p.getFrequency();
        double l_d = p.getDocumentLength();
        double nD = getNumberOfNonZeroLengthDocuments();
        double df = documentFrequency;
        double l_c = numberOfTokens * nD;
        double nT = numberOfUniqueTerms;
        double nT_d = getNumberOfDocumentUniqueTerms(docId);

        return score(tfd, l_d, nD, nT_d, df, l_c, nT);
    }

    public double score(double tfd, double l_d, double nD, double nT_d, double df, double l_c, double nT) {
        try {
            double pivdv = getPivotedVerboseness(l_d, nT_d, l_c, nT);
            double pivdl = getPivotedLength(l_d, l_c, nD);

            double KD = getKD(pivdl, pivdv);
            double TFD = getTFD(tfd, KD);
            double IDF = WeightingModelLibrary.log(nD / df);
            return TFD * IDF;
        } catch (InvalidAlgorithmParameterException e) {
            e.printStackTrace();
            System.exit(1);
        }
        return 0d;
    }

    public double score(double tfd, double l_d) {
        return 0d;
    }

    private double getNumberOfDocumentUniqueTerms(int docId) {
        try {
            initIndex();
            DocumentIndex doi = index.getDocumentIndex();
            DocumentIndexEntry die = doi.getDocumentEntry(docId);
            return die.getNumberOfEntries();
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
        return 0;
    }

    private double getPivotedLength(double l_d, double l_c, double nD) throws InvalidAlgorithmParameterException {
        double res;
        if (tfNormalizationPivotization.equals("elite") || tfNormalizationPivotization.equals("non_elite")) {
            res = l_d / (l_c / nD);
        } else {
            throw new InvalidAlgorithmParameterException("The value of the tf.normalization.pivotization is invalid: " + tfNormalizationPivotization);
        }
        return res;
    }

    private void initIndex() {
        if (index == null)
            index = rq.getIndex();
    }

    private double getPivotedVerboseness(double l_d, double nT_d, double l_c, double nT) throws InvalidAlgorithmParameterException {
        double res;
        if (tfNormalizationPivotization.equals("non_elite")) {
            res = (l_d / nT_d) / (l_c / nT);
        } else if (tfNormalizationPivotization.equals("elite")) {
            res = (l_d / nT_d) / getAverageVerboseness();
        } else {
            throw new InvalidAlgorithmParameterException("The value of the tf.normalization.pivotization is invalid: " + tfNormalizationPivotization);
        }
        return res;
    }

    private int getNumberOfNonZeroLengthDocuments() {
        int res = nZnD;
        if (res < 0) {
            try {
                res = 0;
                initIndex();
                DocumentIndex doi = index.getDocumentIndex();
                int nD = doi.getNumberOfDocuments();
                int nZnD = 0;
                for (int i = 0; i < nD; i++) {
                    DocumentIndexEntry die = doi.getDocumentEntry(i);
                    double l_d = die.getDocumentLength();
                    if(l_d > 0) {
                        nZnD++;
                    }
                }
                res = nZnD;
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(1);
            }
            nZnD = res;
        }
        return res;
    }


    private double getAverageVerboseness() {
        double res = avgV;
        if (res < 0) {
            try {
                res = 0d;
                initIndex();
                DocumentIndex doi = index.getDocumentIndex();
                int nD = doi.getNumberOfDocuments();
                int nZnD = 0;
                for (int i = 0; i < nD; i++) {
                    DocumentIndexEntry die = doi.getDocumentEntry(i);
                    double l_d = die.getDocumentLength();
                    if(l_d > 0) {
                        double nT_d = die.getNumberOfEntries();
                        res += l_d / nT_d;
                        nZnD++;
                    }
                }
                res /= nZnD;
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(1);
            }
            avgV = res;
        }
        return res;
    }

    private double getKD(double pivdl, double pivdv) throws InvalidAlgorithmParameterException {
        double res;
        if (tfNormalizationCombination.equals("linear")) {
            res = 1d - b + b * (1d - a) * pivdl + b * a * pivdv;
        } else if (tfNormalizationCombination.equals("product")) {
            res = Math.pow(pivdl, b * (1d - a)) * Math.pow(pivdv, b * a);
        } else {
            throw new InvalidAlgorithmParameterException("The value of the tf.normalization.combination is invalid: " + tfNormalizationCombination);
        }
        return k_1 * res;
    }

    private double getTFD(double tfd, double Kd) throws InvalidAlgorithmParameterException {
        double res;
        if (tfQuantification.equals("total")) {
            res = tfd / Kd;
        } else if (tfQuantification.equals("log")) {
            res = WeightingModelLibrary.log(tfd / Kd + 1d);
        } else if (tfQuantification.equals("bm25")) {
            res = 2d * tfd / (tfd + Kd);
        } else if (tfQuantification.equals("constant")) {
            res = 1d / Kd;
        } else {
            throw new InvalidAlgorithmParameterException("The value of the tf.quantification is invalid: " + tfQuantification);
        }
        return res;
    }

    public void setParameter(double _b) {}

    public double getParameter() {
        return 0;
    }
}
