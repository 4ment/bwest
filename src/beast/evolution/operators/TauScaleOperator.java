/*
 * Copyright (C) 2016 Mathieu Fourment <mathieu.fourment@uts.edu.au>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package beast.evolution.operators;

import beast.core.Description;
import beast.core.Input;
import beast.core.Operator;
import beast.evolution.tree.Node;
import beast.evolution.tree.Tree;
import beast.util.Randomizer;

import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by mathieu on 19/08/2016.
 */

@Description("Scales one intercoalescent interval or every node height above a node chosen at random.")
public class TauScaleOperator extends Operator {

    public final Input<Tree> treeInput = new Input<>("tree", "beast.tree intercoalescent times are scaled", Input.Validate.REQUIRED);

    final public Input<Double> scaleFactorInput = new Input<>("scaleFactor", "scaling factor: larger means more bold proposals", 1.0);

    final public Input<Boolean> oneIntervalOnlyInput = new Input<>("oneIntervalOnly", "choose one interval and scale it (default true)", true);

    final public Input<Boolean> optimiseInput = new Input<>("optimise", "flag to indicate that the scale factor is automatically changed in order to achieve a good acceptance rate (default true)", true);


    final public Input<Double> scaleUpperLimit = new Input<>("upper", "Upper Limit of scale factor", 1.0 - 1e-8);
    final public Input<Double> scaleLowerLimit = new Input<>("lower", "Lower limit of scale factor", 1e-8);

    private double m_fScaleFactor;
    private double upper, lower;
    private Tree tree;

    private Set<Integer>[] tauMap;
    private List<Integer> intervals;

    @Override
    public void initAndValidate() {
        m_fScaleFactor = scaleFactorInput.get();
        upper = scaleUpperLimit.get();
        lower = scaleLowerLimit.get();
        tree = treeInput.get();

        if (oneIntervalOnlyInput.get()) {
            intervals = tree.getInternalNodes().stream().map(node -> node.getNr()).collect(Collectors.toList());
            intervals.add(0);

            intervals.sort((node1, node2) -> Double.compare(tree.getNode(node1).getHeight(), tree.getNode(node2).getHeight()));

            tauMap = new HashSet[tree.getInternalNodeCount()];
            for (int i = 0; i < tauMap.length; i++) {
                tauMap[i] = new HashSet<>();
            }

            // For each interval, add nodes with branch included in this interval
            for (Node node : tree.getInternalNodes()) {
                double height = Math.min(node.getLeft().getHeight(), node.getRight().getHeight());
                for (int i = 1; i < intervals.size(); i++) {
                    double endInterval = tree.getNode(intervals.get(i)).getHeight();
                    double startInterval = tree.getNode(intervals.get(i - 1)).getHeight();
                    if (node.getHeight() >= endInterval && startInterval >= height) {
                        tauMap[i - 1].add(node.getNr());
                    }
                }
            }

            // For each interval, add every node above this interval
            for (int i = tauMap.length - 1; i > 0; i--) {
                tauMap[i - 1].addAll(tauMap[i]);
            }
        }
    }

    protected double getScaler() {
        return (m_fScaleFactor + (Randomizer.nextDouble() * ((1.0 / m_fScaleFactor) - m_fScaleFactor)));
    }

    @Override
    public double proposal() {
        double hastingsRatio;
        final double scale = getScaler();

        if (oneIntervalOnlyInput.get()) {
            int index = Randomizer.nextInt(tauMap.length);
            double oldInterval = tree.getNode(intervals.get(index + 1)).getHeight() - tree.getNode(intervals.get(index)).getHeight();
            double newIntervalTime = scale * oldInterval;
            double intervalInc = newIntervalTime - oldInterval;

            for (Integer nodeNr : tauMap[index]) {
                Node node = tree.getNode(nodeNr);
                double height = node.getHeight();
                node.setHeight(height + intervalInc);
            }
            hastingsRatio = -Math.log(scale);
        } else {
            int index = Randomizer.nextInt(tree.getInternalNodeCount());
            Node node = tree.getNode(index + tree.getLeafNodeCount());
            final double nodeHeight = node.getHeight();
            final double newHeight = nodeHeight * scale;
            if (newHeight < Math.max(node.getLeft().getHeight(), node.getRight().getHeight())) {
                return Double.NEGATIVE_INFINITY;
            }

            int internalNodes = 0;
            for (int i = tree.getLeafNodeCount(); i < tree.getNodeCount(); i++) {
                final double height = tree.getNode(i).getHeight();
                if (height >= nodeHeight) {
                    tree.getNode(i).setHeight(height * scale);
                }
            }
            hastingsRatio = Math.log(scale) * (internalNodes - 2);
        }
        return hastingsRatio;
    }


    /**
     * automatic parameter tuning *
     */
    @Override
    public void optimize(final double logAlpha) {
        if (optimiseInput.get()) {
            double delta = calcDelta(logAlpha);
            delta += Math.log(1.0 / m_fScaleFactor - 1.0);
            setCoercableParameterValue(1.0 / (Math.exp(delta) + 1.0));
        }
    }

    @Override
    public double getCoercableParameterValue() {
        return m_fScaleFactor;
    }

    @Override
    public void setCoercableParameterValue(final double value) {
        m_fScaleFactor = Math.max(Math.min(value, upper), lower);
    }

    @Override
    public String getPerformanceSuggestion() {
        final double prob = m_nNrAccepted / (m_nNrAccepted + m_nNrRejected + 0.0);
        final double targetProb = getTargetAcceptanceProbability();

        double ratio = prob / targetProb;
        if (ratio > 2.0) ratio = 2.0;
        if (ratio < 0.5) ratio = 0.5;

        // new scale factor
        final double sf = Math.pow(m_fScaleFactor, ratio);

        final DecimalFormat formatter = new DecimalFormat("#.###");
        if (prob < 0.10) {
            return "Try setting scaleFactor to about " + formatter.format(sf);
        } else if (prob > 0.40) {
            return "Try setting scaleFactor to about " + formatter.format(sf);
        } else return "";
    }
}
