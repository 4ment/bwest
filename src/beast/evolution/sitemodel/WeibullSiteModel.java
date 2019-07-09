package beast.evolution.sitemodel;

import beast.core.Input;
import beast.evolution.tree.Node;

public class WeibullSiteModel extends SiteModel {
    public enum distribution {WEIBULL,GAMMA};
    final public Input<WeibullSiteModel.distribution> distributionInput = new Input<>("distribution", "determines distribution. "
            + "Weibull for Weibull distribution. "
            + "Gamma for gamma distribution. ", distribution.WEIBULL, WeibullSiteModel.distribution.values());

    @Override
    protected void calculateCategoryRates(final Node node) {
        if (distributionInput.get() == distribution.GAMMA) {
            super.calculateCategoryRates(node);
            return;
        }
        double propVariable = 1.0;
        int cat = 0;

        if (/*invarParameter != null && */invarParameter.getValue() > 0) {
            if (hasPropInvariantCategory) {
                categoryRates[0] = 0.0;
                categoryProportions[0] = invarParameter.getValue();
            }
            propVariable = 1.0 - invarParameter.getValue();
            if (hasPropInvariantCategory) {
                cat = 1;
            }
        }

        if (shapeParameter != null) {

            final double a = shapeParameter.getValue();
            double mean = 0.0;
            final int gammaCatCount = categoryCount - cat;

            for (int i = 0; i < gammaCatCount; i++) {
                categoryRates[i + cat] = Math.pow(-Math.log(1.0 - (2.0 * i + 1.0) / (2.0 * gammaCatCount)), 1.0 / a);
                mean += categoryRates[i + cat];

                categoryProportions[i + cat] = propVariable / gammaCatCount;
            }

            mean = (propVariable * mean) / gammaCatCount;

            for (int i = 0; i < gammaCatCount; i++) {

                categoryRates[i + cat] /= mean;
            }
        } else {
            categoryRates[cat] = 1.0 / propVariable;
            categoryProportions[cat] = propVariable;
        }


        ratesKnown = true;
    }
}
