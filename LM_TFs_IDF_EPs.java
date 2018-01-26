package at.ac.tuwien.ifs;

import org.terrier.matching.models.WeightingModel;
import org.terrier.matching.models.WeightingModelLibrary;
import org.terrier.structures.*;
import org.terrier.structures.postings.Posting;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;

/**
 * This class implements the LM_TF_IDF weighting model with verboseness and 4 TF Quantifications,
 * and with Exposed ParameterS, presented by Lipani et al. in the paper "*".
 *
 * @author Aldo Lipani
 */
public class LM_TFs_IDF_EPs extends WeightingModel {

    private static final long serialVersionUID = 1L;

    private static Index index;

    /**
     * tf normalization combination
     **/
    private String lambdaqNormalizationCombination = "linear";

    public static String[] lambdaqNormalizationCombinations = new String[]{"linear", "product"};
    /**
     * tf normalization pivotization
     **/
    private String lambdaqNormalizationPivotization = "non_elite";

    public static String[] lambdaqNormalizationPivotizations = new String[]{"non_elite", "elite"};

    /**
     * model name
     */
    private static final String name = "LM_TFs_IDF_EPs";

    /**
     * The constant k_1.
     */

    /**
     * The constant bs.
     */
    private double b = 0.50d;
    private double a = 0.50d;

    public static double avgB = -1d;
    public static int nZnD = -1;

    /**
     * A default constructor to make this model.
     */
    public LM_TFs_IDF_EPs() {
        super();
        this.lambdaqNormalizationCombination = System.getProperty("lambdaq.normalization.combination", "linear").toLowerCase();
        this.lambdaqNormalizationPivotization = System.getProperty("lambdaq.normalization.pivotization", "non_elite").toLowerCase();
        this.b = Double.parseDouble(System.getProperty("b", "0.5d"));
        this.a = Double.parseDouble(System.getProperty("a", "0.5d"));
    }

    public LM_TFs_IDF_EPs(String lambdaqNormalizationCombination,
                          String lambdaqNormalizationPivotization,
                          double b,
                          double a) {
        super();
        this.lambdaqNormalizationCombination = lambdaqNormalizationCombination;
        this.lambdaqNormalizationPivotization = lambdaqNormalizationPivotization;
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
                ".nc_" + lambdaqNormalizationCombination +
                ".np_" + lambdaqNormalizationPivotization +
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
        double tfd = p.getFrequency();
        double nD = getNumberOfNonZeroLengthDocuments();
        double df = documentFrequency;
        double l_c = averageDocumentLength * nD;
        double l_t = termFrequency;

        return score(tfd, nD, df, l_c, l_t);
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


    public double score(double tfd, double nD, double df, double l_c, double l_t) {
        try {
            double pivtb = getPivotedTermBurstiness(l_t, df, l_c, nD);
            double pivtl = getPivotedTermLength(l_t, l_c, nD);

            double KT = getKD(pivtl, pivtb);
            double lambdaq = KT / (KT + 1);
            double TFD = tfd;
            double IDF = WeightingModelLibrary.log(1d - lambdaq + lambdaq * nD / df);
            //double IDF = WeightingModelLibrary.log(1d + KT * l_c / l_t);
            return TFD * IDF;
        } catch (InvalidAlgorithmParameterException ex) {
            ex.printStackTrace();
            return 0d;
        }
    }

    public double score(double tfd, double l_d) {
        return 0d;
    }

    private double getPivotedTermLength(double l_t, double l_c, double nD) throws InvalidAlgorithmParameterException {
        double res;
        if (lambdaqNormalizationPivotization.equals("elite") || lambdaqNormalizationPivotization.equals("non_elite")) {
            res = l_t / (l_c / nD);
        } else {
            throw new InvalidAlgorithmParameterException("The value of the lambdaq.normalization.pivotization is invalid: " + lambdaqNormalizationPivotization);
        }
        return res;
    }

    private void initIndex() {
        if (index == null)
            index = rq.getIndex();
    }

    private double getPivotedTermBurstiness(double l_t, double nD_t, double l_c, double nD) throws InvalidAlgorithmParameterException {
        double res;
        if (lambdaqNormalizationPivotization.equals("non_elite")) {
            res = (l_t / nD_t) / (l_c / nD);
        } else if (lambdaqNormalizationPivotization.equals("elite")) {
            res = (l_t / nD_t) / getAverageTermBurstiness();
        } else {
            throw new InvalidAlgorithmParameterException("The value of the lambdaq.normalization.pivotization is invalid: " + lambdaqNormalizationPivotization);
        }
        return res;
    }

    private double getAverageTermBurstiness() {
        double res = avgB;
        if (res < 0) {
            res = 0d;
            initIndex();
            Lexicon<String> lex = index.getLexicon();
            int nT = lex.numberOfEntries();
            for (int i = 0; i < nT; i++) {
                LexiconEntry tie = lex.getIthLexiconEntry(i).getValue();
                double l_t = tie.getFrequency();
                double nD_t = tie.getDocumentFrequency();
                res += l_t / nD_t;
            }
            res /= nT;
            avgB = res;
        }
        return res;
    }

    private double getKD(double pivtl, double pivtb) throws InvalidAlgorithmParameterException {
        double res;
        if (lambdaqNormalizationCombination.equals("linear")) {
            res = 1d - b + b * (1d - a) * pivtl + b * a * pivtb;
        } else if (lambdaqNormalizationCombination.equals("product")) {
            res = Math.pow(pivtl, b * (1d - a)) * Math.pow(pivtb, b * a);
        } else {
            throw new InvalidAlgorithmParameterException("The value of the lambdaq.normalization.combination is invalid: " + lambdaqNormalizationCombination);
        }
        return res;
    }


    public void setParameter(double _b) {
    }

    public double getParameter() {
        return 0;
    }

}
