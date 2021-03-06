package edu.stanford.nlp.coref.statistical;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.coref.CorefAlgorithm;
import edu.stanford.nlp.coref.CorefProperties;
import edu.stanford.nlp.coref.CorefUtils;
import edu.stanford.nlp.coref.data.CorefCluster;
import edu.stanford.nlp.coref.data.Dictionaries;
import edu.stanford.nlp.coref.data.Dictionaries.MentionType;
import edu.stanford.nlp.coref.data.Document;
import edu.stanford.nlp.coref.data.Mention;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.RuntimeInterruptedException;

/**
 * Does best-first coreference resolution by linking each mention to its highest scoring candidate
 * antecedent if that score is above a threshold. The model is described in
 * <p>
 * Kevin Clark and Christopher D. Manning. 2015.
 * <a href="http://nlp.stanford.edu/pubs/clark-manning-acl15-entity.pdf">
 * Entity-Centric Coreference Resolution with Model Stacking</a>.
 * In Association for Computational Linguistics.
 * <p>
 * See {@link StatisticalCorefTrainer} for training a new model.
 *
 * @author Kevin Clark
 */
public class StatisticalCorefAlgorithm implements CorefAlgorithm {

    private final Map<Pair<Boolean, Boolean>, Double> thresholds;
    private final FeatureExtractor extractor;
    private final PairwiseModel classifier;
    private final int maxMentionDistance;
    private final int maxMentionDistanceWithStringMatch;

    public StatisticalCorefAlgorithm(Properties props, Dictionaries dictionaries) {
        this(props, dictionaries,
            StatisticalCorefProperties.wordCountsPath(props),
            StatisticalCorefProperties.rankingModelPath(props),
            CorefProperties.maxMentionDistance(props),
            CorefProperties.maxMentionDistanceWithStringMatch(props),
            StatisticalCorefProperties.pairwiseScoreThresholds(props));
    }

    public StatisticalCorefAlgorithm(Properties props, Dictionaries dictionaries, String wordCountsFile,
                                     String modelFile, int maxMentionDistance, int maxMentionDistanceWithStringMatch,
                                     double threshold) {
        this(props, dictionaries, wordCountsFile, modelFile, maxMentionDistance,
            maxMentionDistanceWithStringMatch, new double[]{threshold, threshold, threshold,
                threshold});
    }

    public StatisticalCorefAlgorithm(Properties props, Dictionaries dictionaries, String wordCountsFile,
                                     String modelPath, int maxMentionDistance, int maxMentionDistanceWithStringMatch,
                                     double[] thresholds) {
        extractor = new FeatureExtractor(props, dictionaries, null, wordCountsFile);
        classifier = PairwiseModel.newBuilder("classifier",
            MetaFeatureExtractor.newBuilder().build()).modelPath(modelPath).build();
        this.maxMentionDistance = maxMentionDistance;
        this.maxMentionDistanceWithStringMatch = maxMentionDistanceWithStringMatch;
        this.thresholds = makeThresholds(thresholds);
    }

    private static Map<Pair<Boolean, Boolean>, Double> makeThresholds(double[] thresholds) {
        Map<Pair<Boolean, Boolean>, Double> thresholdsMap = new HashMap<>();
        thresholdsMap.put(new Pair<>(true, true), thresholds[0]);
        thresholdsMap.put(new Pair<>(true, false), thresholds[1]);
        thresholdsMap.put(new Pair<>(false, true), thresholds[2]);
        thresholdsMap.put(new Pair<>(false, false), thresholds[3]);
        return thresholdsMap;
    }

    @Override
    public void runCoref(Document document) {
        // Actually Run coref
        try {
            File tempFile = new File("temp.out");

            BufferedWriter tempFileWriter = new BufferedWriter(new FileWriter(tempFile));

            Compressor<String> compressor = new Compressor<>();
            if (Thread.interrupted()) {  // Allow interrupting
                throw new RuntimeInterruptedException();
            }

            Map<Pair<Integer, Integer>, Boolean> pairs = new HashMap<>();

            for (Map.Entry<Integer, List<Integer>> e : CorefUtils.heuristicFilter(
                CorefUtils.getSortedMentions(document),
                maxMentionDistance, maxMentionDistanceWithStringMatch).entrySet()) {
                for (int m1 : e.getValue()) {
                    pairs.put(new Pair<>(m1, e.getKey()), true);
                }
            }

            DocumentExamples examples = extractor.extract(0, document, pairs, compressor);
            Counter<Pair<Integer, Integer>> pairwiseScores = new ClassicCounter<>();
            for (Example mentionPair : examples.examples) {
                if (Thread.interrupted()) {  // Allow interrupting
                    throw new RuntimeInterruptedException();
                }
                pairwiseScores.incrementCount(new Pair<>(mentionPair.mentionId1, mentionPair.mentionId2),
                    classifier.predict(mentionPair, examples.mentionFeatures, compressor));
            }

            List<Pair<Integer, Integer>> mentionPairs = new ArrayList<>(pairwiseScores.keySet());
            mentionPairs.sort((p1, p2) -> {
                double diff = pairwiseScores.getCount(p2) - pairwiseScores.getCount(p1);
                return diff == 0 ? 0 : (int) Math.signum(diff);
            });

            Set<Integer> seenAnaphors = new HashSet<>();
            for (Pair<Integer, Integer> pair : mentionPairs) {

                if (seenAnaphors.contains(pair.second)) {
                    continue;
                }
                if (Thread.interrupted()) {  // Allow interrupting
                    throw new RuntimeInterruptedException();
                }
                seenAnaphors.add(pair.second);
                Mention
                    m1 = document.predictedMentionsByID.get(pair.first),
                    m2 = document.predictedMentionsByID.get(pair.second);


                MentionType mt1 = m1.mentionType;
                MentionType mt2 = m2.mentionType;
                if (pairwiseScores.getCount(pair) > thresholds.get(new Pair<>(mt1 == MentionType.PRONOMINAL,
                    mt2 == MentionType.PRONOMINAL))) {
                    // The CorefUtils.mergeCoreferenceClusters is modified
                    CorefUtils.mergeCoreferenceClusters(pair, document);
                }
            }
        } catch (IOException x) {
            System.exit(-1);
        }
    }
}
