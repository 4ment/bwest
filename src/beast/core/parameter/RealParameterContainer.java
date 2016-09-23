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

package beast.core.parameter;

import beast.core.Description;
import beast.core.Input;
import beast.core.StateNode;
import org.apache.commons.math3.optim.linear.Relationship;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.io.PrintStream;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by mathieu on 19/08/2016.
 */
@Description("Provides a container for parameters that need to be grouped together." +
        " For example, this class allows using a dirichlet prior and the DeltaExchangeOperator" +
        " operator on the relative rates of the GTR model.")
public class RealParameterContainer extends RealParameter {

    public Input<List<RealParameter>> parametersInput = new Input<>(
            "parameter", "List of parameters, each of dimension 1.", new ArrayList<>());

    protected List<RealParameter> parameters;

    @Override
    public void initAndValidate() {
        super.initAndValidate();
        parameters = parametersInput.get();

        if (m_fLower == Double.NEGATIVE_INFINITY){
            m_fLower = parameters.get(0).getLower();
        }
        if (m_fUpper == Double.POSITIVE_INFINITY){
            m_fUpper = parameters.get(0).getUpper();
        }
    }


    // Function methods
    @Override
    public int getDimension() {
        return parameters.size();
    }

    @Override
    public double getArrayValue() {
        return parameters.get(0).getValue();
    }

    @Override
    public double getArrayValue(int i) {
        return parameters.get(i).getValue();
    }

    // Parameter.Base

    /**
     * @param index dimension to check
     * @return true if the param-th element has changed
     */
    public boolean isDirty(final int index) {
        return parameters.get(index).isDirty(0);
    }

    /**
     * Returns index of entry that was changed last. Useful if it is known
     * only a single value has changed in the array. *
     */
    public int getLastDirty() {
        return m_nLastDirty;
    }

    @Override
    public void setDimension(final int dimension) {
        throw new IllegalArgumentException("Changing dimension is not allowed in RealParameterContainer class");
    }

    @Override
    public void setMinorDimension(final int dimension) {
        throw new IllegalArgumentException("Changing dimension is not allowed in RealParameterContainer class");
    }

    @Override
    public Double getValue() {
        return parameters.get(0).getValue();
    }

    @Override
    public Double getLower() {
        return m_fLower;
    }

    @Override
    public void setLower(final Double lower) {
        m_fLower = lower;
        for(RealParameter parameter: parameters){
            parameter.setLower(lower);
        }
    }

    @Override
    public Double getUpper() {
        return m_fUpper;
    }

    @Override
    public void setUpper(final Double upper) {
        m_fUpper = upper;
        for(RealParameter parameter: parameters){
            parameter.setUpper(upper);
        }
    }

    @Override
    public Double getValue(final int param) {
        return parameters.get(param).getValue();
    }

    @Override
    public Double[] getValues() {
        Double [] values = new Double[getDimension()];
        for (int i = 0; i < getDimension(); i++){
            values[i] = getValue(i);
        }
        return values;
    }

    public void setBounds(final Double lower, final Double upper) {
        setLower(lower);
        setUpper(upper);
    }

    @Override
    public void setValue(final Double value) {
        parameters.get(0).setValue(value);
        m_nLastDirty = 0;
    }

    @Override
    public void setValue(final int param, final Double value) {
        parameters.get(param).setValue(value);
        m_nLastDirty = param;
    }

    @Override
    public void swap(final int left, final int right) {
        double tmp = getValue(left);
        setValue(left, getValue(right));
        setValue(right, tmp);
    }

    /**
     * Note that changing toString means fromXML needs to be changed as
     * well, since it parses the output of toString back into a parameter.
     */
    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        buf.append(getID()).append("[").append(getDimension());
        if (minorDimension > 0) {
            buf.append(" ").append(minorDimension);
        }
        buf.append("] ");
        buf.append("(").append(m_fLower).append(",").append(m_fUpper).append("): ");
        for (final Double value : getValues()) {
            buf.append(value).append(" ");
        }
        return buf.toString();
    }

    @Override
    public RealParameterContainer copy() {
        try {
            @SuppressWarnings("unchecked")
            final RealParameterContainer copy = (RealParameterContainer) this.clone();
            copy.parameters = new ArrayList<>();
            for (RealParameter parameter:  parameters){
                copy.parameters.add((RealParameter) parameter.copy());
            }
            return copy;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void assignTo(final StateNode other) {
        @SuppressWarnings("unchecked")
        final RealParameterContainer copy = (RealParameterContainer) other;
        copy.setID(getID());
        copy.index = index;
        copy.setLower(m_fLower);
        copy.setUpper(m_fUpper);
        for (int i = 0; i < getDimension(); i++){
            parameters.get(i).assignTo(copy.parameters.get(i));
        }
    }

    @Override
    public void assignFrom(final StateNode other) {
        @SuppressWarnings("unchecked")
        final RealParameterContainer source = (RealParameterContainer) other;
        setID(source.getID());
        setLower(source.m_fLower);
        setUpper(source.m_fUpper);
        for (int i = 0; i < getDimension(); i++){
            parameters.get(i).assignFrom(source.parameters.get(i));
        }
    }

    @Override
    public void assignFromFragile(final StateNode other) {
        @SuppressWarnings("unchecked")
        final RealParameterContainer source = (RealParameterContainer) other;
        for (int i = 0; i < getDimension(); i++){
            parameters.get(i).assignFromFragile(source.parameters.get(i));
        }
    }

    /**
     * Loggable interface implementation follows (partly, the actual logging
     * of values happens in derived classes) *
     */
    @Override
    public void init(PrintStream out) {
        for (int i = 0; i < getDimension(); i++){
            out.print(parameters.get(i).getID() + "\t");
        }
    }

    @Override
    public void log(int nSample, PrintStream out) {
        for (int i = 0; i < getDimension(); i++)
            out.print(getArrayValue(i) + "\t");
    }

    /**
     * StateNode implementation *
     */

    @Override
    public int scale(final double scale) {
        for (int i = 0; i < getDimension(); i++) {
            double value = getValue(i)*scale;
            if (value < m_fLower || value > m_fUpper) {
                throw new IllegalArgumentException("parameter scaled our of range");
            }
            setValue(i, value);
        }
        return getDimension();
    }

    @Override
    public void fromXML(final Node node) {
        final NamedNodeMap atts = node.getAttributes();
        setID(atts.getNamedItem("id").getNodeValue());
        final String str = node.getTextContent();
        Pattern pattern = Pattern.compile(".*\\[(.*) (.*)\\].*\\((.*),(.*)\\): (.*) ");
        Matcher matcher = pattern.matcher(str);

        if (matcher.matches()) {
            final String dimension = matcher.group(1);
            final String stride = matcher.group(2);
            final String lower = matcher.group(3);
            final String upper = matcher.group(4);
            final String valuesAsString = matcher.group(5);
            final String[] values = valuesAsString.split(" ");
            minorDimension = Integer.parseInt(stride);
            fromXML(Integer.parseInt(dimension), lower, upper, values);
        } else {
            pattern = Pattern.compile(".*\\[(.*)\\].*\\((.*),(.*)\\): (.*) ");
            matcher = pattern.matcher(str);
            if (matcher.matches()) {
                final String dimension = matcher.group(1);
                final String lower = matcher.group(2);
                final String upper = matcher.group(3);
                final String valuesAsString = matcher.group(4);
                final String[] values = valuesAsString.split(" ");
                minorDimension = 0;
                fromXML(Integer.parseInt(dimension), lower, upper, values);
            } else {
                throw new RuntimeException("parameter could not be parsed");
            }
        }
    }

    @Override
    void fromXML(final int dimension, final String lower, final String upper, final String[] valuesString) {
        setLower(Double.parseDouble(lower));
        setUpper(Double.parseDouble(upper));
        for (int i = 0; i < valuesString.length; i++) {
            parameters.get(i).fromXML(1, lower, upper, new String[]{valuesString[i]});
        }
    }

    /**
     * matrix implementation *
     */

    @Override
    public Double getMatrixValue(final int i, final int j) {
        return getValue(i * minorDimension + j);
    }

    @Override
    public void getMatrixValues1(final int i, final Double[] row) {
        assert (row.length == minorDimension);
        for (int j = 0; j < minorDimension; j++){
            row[j] = getArrayValue(i * minorDimension + j);
        }
    }

    @Override
    public void getMatrixValues2(final int j, final Double[] col) {
        assert (col.length == getMinorDimension2());
        for (int i = 0; i < getMinorDimension2(); i++){
            col[i] = getArrayValue(i * minorDimension + j);
        }
    }
}
