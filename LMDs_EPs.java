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
 * This class implements the LM weighting model with verboseness and with
 * Exposed ParameterS, presented by Lipani et al. in the paper "*".
 *
 * @author Aldo Lipani
 */
public class LMDs_EPs extends WeightingModel {

    private static final long serialVersionUID = 1L;

    private static Index index;

    /**
     * tf normalization combination
     **/
    private String tfNormalizationCombination = "linear";

    public static String[] tfNormalizationCombinations = new String[]{"linear", "product"};

    /**
     * tf normalization pivotization
     **/
    private String tfNormalizationPivotization = "non_elite";

    public static String[] tfNormalizationPivotizations = new String[]{"non_elite", "elite"};


    /**
     * model name
     */
    private static final String name = "LMDs_EPs";

    /**
     * The constant bs.
     */
    private double b = 0.50d;
    private double a = 0.50d;

    public static double avgV = -1d;

    public static int nZnD = -1;

    /**
     * A default constructor to make this model.
     */
    public LMDs_EPs() {
        super();
        this.tfNormalizationCombination = System.getProperty("tf.normalization.combination", "linear").toLowerCase();
        this.tfNormalizationPivotization = System.getProperty("tf.normalization.pivotization", "non_elite").toLowerCase();
        this.b = Double.parseDouble(System.getProperty("b", "0.5d"));
        this.a = Double.parseDouble(System.getProperty("a", "0.5d"));
    }

    public LMDs_EPs(String tfNormalizationCombination,
                    String tfNormalizationPivotization,
                    double b,
                    double a) {
        super();
        this.tfNormalizationCombination = tfNormalizationCombination;
        this.tfNormalizationPivotization = tfNormalizationPivotization;
        this.b = b;
        this.a = a;
    }


    /**
     * Returns the name of the model, in this case "TFs_IDF"
     *
     * @return the name of the model
     */
    public final String getInfo() {
        return name +
                ".nc_" + tfNormalizationCombination +
                ".np_" + tfNormalizationPivotization +
                ".b_" + String.format("%.1f", b) +
                ".a_" + String.format("%.2f", a);
    }

    /**
     * Uses TF_IDF to compute a weight for a term in a document.
     *
     * @param p The posting of the term of the term in the document
     * @return the score assigned to a document with the given
     * tf and docLength, and other preset parameters
     */
    @Override
    public double score(Posting p) {
        int docId = p.getId();
        double tfd = p.getFrequency();
        double l_d = p.getDocumentLength();
        double nD = getNumberOfNonZeroLengthDocuments();
        double l_c = numberOfTokens * nD;
        double nT = numberOfUniqueTerms;
        double l_t = termFrequency;
        double nT_d = getNumberOfDocumentUniqueTerms(docId);

        return score(tfd, l_d, nD, l_c, nT, l_t, nT_d);
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

    public double score(double tfd, double l_d, double nD, double l_c, double nT, double l_t, double nT_d) {
        try {
            double pivdv = getPivotedVerboseness(l_d, nT_d, l_c, nT);
            double pivdl = getPivotedLength(l_d, l_c, nD);

            double KD = getKD(pivdl, pivdv);
            double lambda = KD / (KD + 1d);
            double TFD = getTFD(tfd, l_d);
            double ILF = getILF(l_t, l_c);
            return WeightingModelLibrary.log(1d - lambda + lambda * TFD * ILF);
        } catch (InvalidAlgorithmParameterException ex) {
            ex.printStackTrace();
            System.exit(1);
        }
        return 0d;
    }

    private double getTFD(double tfd, double l_d) {
        return tfd / l_d;
    }

    private double getILF(double l_t, double l_c) {
        return l_c / l_t;
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
        return res;
    }

    public void setParameter(double _b) {
    }

    public double getParameter() {
        return 0;
    }
}
