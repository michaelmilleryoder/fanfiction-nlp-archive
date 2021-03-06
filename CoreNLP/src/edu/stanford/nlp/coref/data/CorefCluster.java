//
// StanfordCoreNLP -- a suite of NLP tools
// Copyright (c) 2009-2010 The Board of Trustees of
// The Leland Stanford Junior University. All Rights Reserved.
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 1A
//    Stanford CA 94305-9010
//    USA
//

package edu.stanford.nlp.coref.data;

import java.io.Serializable;
import java.util.*;

import edu.stanford.nlp.coref.data.Dictionaries.Animacy;
import edu.stanford.nlp.coref.data.Dictionaries.Gender;
import edu.stanford.nlp.coref.data.Dictionaries.Number;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * One cluster for the SieveCoreferenceSystem.
 *
 * @author Heeyoung Lee
 */
public class CorefCluster implements Serializable {

    private static final long serialVersionUID = 8655265337578515592L;

    public final Set<Mention> corefMentions;
    public final int clusterID;

    // Attributes for cluster - can include multiple attribute e.g., {singular, plural}
    public final Set<Number> numbers;
    public final Set<Gender> genders;
    public final Set<Animacy> animacies;
    public final Set<String> nerStrings;
    public final Set<String> heads;
    public final HashMap<String, Integer> characterCounts;

    /**
     * All words in this cluster - for word inclusion feature
     */
    public final Set<String> words;

    /**
     * The first mention in this cluster
     */
    protected Mention firstMention;
    public String character = "NO_ASSIGNED_CHARACTER";

    public Gender gender = Gender.UNKNOWN;
    HashMap<Gender, Integer> genderCounts;

    /**
     * Return the most representative mention in the chain.
     * A proper noun mention or a mention with more pre-modifiers is preferred.
     */
    public Mention representative;

    public int getClusterID() {
        return clusterID;
    }

    public Set<Mention> getCorefMentions() {
        return corefMentions;
    }

    public int size() {
        return corefMentions.size();
    }

    public Mention getFirstMention() {
        return firstMention;
    }

    public Mention getRepresentativeMention() {
        return representative;
    }

    public CorefCluster(int ID) {
        clusterID = ID;
        corefMentions = Generics.newHashSet();
        numbers = EnumSet.noneOf(Number.class);
        genders = EnumSet.noneOf(Gender.class);
        animacies = EnumSet.noneOf(Animacy.class);
        nerStrings = Generics.newHashSet();
        heads = Generics.newHashSet();
        words = Generics.newHashSet();
        firstMention = null;
        representative = null;
        character = "";
        characterCounts = new HashMap<String, Integer>();
        genderCounts = new HashMap<>();
    }

    public CorefCluster(int ID, Set<Mention> mentions) {
        this(ID);
        // Register mentions
        corefMentions.addAll(mentions);
        // Get list of mentions in textual order
        List<Mention> sortedMentions = new ArrayList<>(mentions.size());
        sortedMentions.addAll(mentions);
        Collections.sort(sortedMentions, new CorefChain.MentionComparator());
        // Set default for first / representative mention
        if (sortedMentions.size() > 0) {
            firstMention = sortedMentions.get(0);
            representative = sortedMentions.get(0); // will be updated below
        }

        for (Mention m : sortedMentions) {
            // Add various information about mentions to cluster
            animacies.add(m.animacy);
            genders.add(m.gender);
            numbers.add(m.number);
            nerStrings.add(m.nerString);
            heads.add(m.headString);
            if (!m.isPronominal()) {
                for (CoreLabel w : m.originalSpan) {
                    words.add(w.get(CoreAnnotations.TextAnnotation.class).toLowerCase());
                }
            }

            // Consider character name of length up to 4
            if (m.mentionType.equals(Dictionaries.MentionType.PROPER) && m.originalSpan.size() <= 4) {
                String mentionStr = m.toString().replace(" 's", "");

                if (characterCounts.containsKey(mentionStr)) {
                    characterCounts.put(mentionStr, characterCounts.get(mentionStr) + 1);
                } else {
                    boolean merged = false;

                    // Merged current character names based on substring
                    for (Map.Entry<String, Integer> entry : characterCounts.entrySet()) {
                        if (mentionStr.contains(entry.getKey())) {
                            characterCounts.put(mentionStr, entry.getValue() + 1);
                            merged = true;
                            break;
                        } else if (entry.getKey().contains(mentionStr)) {
                            entry.setValue(entry.getValue() + 1);
                            merged = true;
                            break;
                        }
                    }

                    if (!merged) {
                        characterCounts.put(mentionStr, 1);
                    }
                }

            }

            genderCounts.put(m.gender, genderCounts.getOrDefault(m.gender, 0) + 1);


            // Update representative mention, if appropriate
            if (m != representative && m.moreRepresentativeThan(representative)) {
                assert !representative.moreRepresentativeThan(m);
                representative = m;
            }
        }

        // Decide the character name based on counts
        if (!characterCounts.isEmpty()) {
            Map.Entry<String, Integer> maxEntry = null;

            for (Map.Entry<String, Integer> entry : characterCounts.entrySet()) {
                if (maxEntry == null || entry.getValue() > maxEntry.getValue()) {
                    maxEntry = entry;
                }
            }

            if (maxEntry != null) {
                this.character = maxEntry.getKey();
            }
        }

        // If a FEMALE/MALE is merged into the cluster, UNKNOWN is removed
        if (genderCounts.containsKey(Gender.FEMALE) || genderCounts.containsKey(Gender.MALE)) {
            genderCounts.remove(Gender.UNKNOWN);
        }

        // Decide the gender based on counts
        if (!genderCounts.isEmpty()) {
            Map.Entry<Gender, Integer> maxEntry = null;

            for (Map.Entry<Gender, Integer> entry : genderCounts.entrySet()) {
                if (maxEntry == null || entry.getValue() > maxEntry.getValue()) {
                    maxEntry = entry;
                }
            }

            if (maxEntry != null) {
                this.gender = maxEntry.getKey();
            }
        }
    }

    /**
     * merge 2 clusters: to = to + from
     */
    public static void mergeClusters(CorefCluster to, CorefCluster from) {
        int toID = to.clusterID;
        for (Mention m : from.corefMentions) {
            m.corefClusterID = toID;
        }
        to.numbers.addAll(from.numbers);


        if (to.numbers.size() > 1 && to.numbers.contains(Number.UNKNOWN)) {
            to.numbers.remove(Number.UNKNOWN);
        }

        to.genders.addAll(from.genders);
        if (to.genders.size() > 1 && to.genders.contains(Gender.UNKNOWN)) {
            to.genders.remove(Gender.UNKNOWN);
        }

        to.animacies.addAll(from.animacies);
        if (to.animacies.size() > 1 && to.animacies.contains(Animacy.UNKNOWN)) {
            to.animacies.remove(Animacy.UNKNOWN);
        }

        to.nerStrings.addAll(from.nerStrings);
        if (to.nerStrings.size() > 1 && to.nerStrings.contains("O")) {
            to.nerStrings.remove("O");
        }
        if (to.nerStrings.size() > 1 && to.nerStrings.contains("MISC")) {
            to.nerStrings.remove("MISC");
        }

        to.heads.addAll(from.heads);
        to.corefMentions.addAll(from.corefMentions);


        to.words.addAll(from.words);
        if (from.firstMention.appearEarlierThan(to.firstMention) && !from.firstMention.isPronominal()) {
            assert !to.firstMention.appearEarlierThan(from.firstMention);
            to.firstMention = from.firstMention;
        }
        if (from.representative.moreRepresentativeThan(to.representative)) to.representative = from.representative;

        // Merge the character name counter for two clusters
        if (!from.characterCounts.isEmpty()) {
            for (Map.Entry<String, Integer> fromEntry : from.characterCounts.entrySet()) {
                if (to.characterCounts.containsKey(fromEntry.getKey())) {
                    to.characterCounts.put(
                        fromEntry.getKey(),
                        to.characterCounts.get(fromEntry.getKey()) + fromEntry.getValue()
                    );
                } else {
                    boolean merged = false;

                    for (Map.Entry<String, Integer> toEntry : to.characterCounts.entrySet()) {
                        if (fromEntry.getKey().contains(toEntry.getKey())) {
                            to.characterCounts.put(
                                fromEntry.getKey(),
                                fromEntry.getValue() + toEntry.getValue()
                            );
                            merged = true;
                            break;
                        } else if (toEntry.getKey().contains(fromEntry.getKey())) {
                            toEntry.setValue(
                                toEntry.getValue() + fromEntry.getValue()
                            );
                            merged = true;
                            break;
                        }
                    }

                    if (!merged) {
                        to.characterCounts.put(fromEntry.getKey(), fromEntry.getValue());
                    }
                }
            }
        }

        // Update the character name for the merged cluster
        if (!to.characterCounts.isEmpty()) {
            Map.Entry<String, Integer> maxEntry = null;

            for (Map.Entry<String, Integer> entry : to.characterCounts.entrySet()) {
                if (maxEntry == null || entry.getValue() > maxEntry.getValue()) {
                    maxEntry = entry;
                }
            }

            to.character = maxEntry.getKey();
        }


        // Merge the gender counter for two clusters
        if (!from.genderCounts.isEmpty()) {

            for (Map.Entry<Gender, Integer> entry : from.genderCounts.entrySet()) {
                to.genderCounts.put(
                    entry.getKey(),
                    to.genderCounts.getOrDefault(entry.getKey(), 0) + entry.getValue()
                );

            }
        }


        if (to.genderCounts.containsKey(Gender.FEMALE) || to.genderCounts.containsKey(Gender.MALE)) {
            to.genderCounts.remove(Gender.UNKNOWN);
        }


        // Update the gender for the merged cluster
        if (!to.genderCounts.isEmpty()) {
            Map.Entry<Gender, Integer> maxEntry = null;

            for (Map.Entry<Gender, Integer> entry : to.genderCounts.entrySet()) {
                if (maxEntry == null || entry.getValue() > maxEntry.getValue()) {
                    maxEntry = entry;
                }
            }

            to.gender = maxEntry.getKey();
        }


        //Redwood.log("debug-cluster", "merged clusters: "+toID+" += "+from.clusterID);
        //to.printCorefCluster();
        //from.printCorefCluster();
    }

    /**
     * Print cluster information
     */
    public void printCorefCluster() {
        Redwood.log("debug-cluster", "Cluster ID: " + clusterID + "\tNumbers: " + numbers + "\tGenders: " + genders + "\tanimacies: " + animacies);
        Redwood.log("debug-cluster", "NE: " + nerStrings + "\tfirst Mention's ID: " + firstMention.mentionID + "\tHeads: " + heads + "\twords: " + words);
        TreeMap<Integer, Mention> forSortedPrint = new TreeMap<>();
        for (Mention m : this.corefMentions) {
            forSortedPrint.put(m.mentionID, m);
        }
        for (Mention m : forSortedPrint.values()) {
            String rep = (representative == m) ? "*" : "";
            if (m.goldCorefClusterID == -1) {
                Redwood.log("debug-cluster", rep + "mention-> id:" + m.mentionID + "\toriginalRef: "
                    + m.originalRef + "\t" + m.spanToString() + "\tsentNum: " + m.sentNum + "\tstartIndex: "
                    + m.startIndex + "\tType: " + m.mentionType + "\tNER: " + m.nerString);
            } else {
                Redwood.log("debug-cluster", rep + "mention-> id:" + m.mentionID + "\toriginalClusterID: "
                    + m.goldCorefClusterID + "\t" + m.spanToString() + "\tsentNum: " + m.sentNum + "\tstartIndex: "
                    + m.startIndex + "\toriginalRef: " + m.originalRef + "\tType: " + m.mentionType + "\tNER: " + m.nerString);
            }
        }
    }

    public boolean isSinglePronounCluster(Dictionaries dict) {
        if (this.corefMentions.size() > 1) return false;
        for (Mention m : this.corefMentions) {
            if (m.isPronominal() || dict.allPronouns.contains(m.spanToString().toLowerCase())) return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return corefMentions.toString() + "=" + clusterID;
    }

}
