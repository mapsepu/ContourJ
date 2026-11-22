package cl.scian.contourj.model;

import cl.scian.contourj.model.helpers.convergence.metrics.ConvergenceMetric;
import cl.scian.contourj.model.helpers.convergence.metrics.ConvergenceMetrics;
import javafx.beans.property.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class ContourAdjustmentParameters { // TODO: Add missing ASL

    private final DoubleProperty alpha = new SimpleDoubleProperty(0.5);
    private final DoubleProperty beta = new SimpleDoubleProperty(0.5);
    private final DoubleProperty gamma = new SimpleDoubleProperty(0.5);
    private final DoubleProperty kappa = new SimpleDoubleProperty(0.5);
    private final DoubleProperty mu = new SimpleDoubleProperty(0.5);
    private final DoubleProperty convergenceThreshold = new SimpleDoubleProperty(0.5);

    private final IntegerProperty iterations = new SimpleIntegerProperty(100);
    private final IntegerProperty gvfIterations = new SimpleIntegerProperty(20);

    // Object views of the numeric properties, used by the GUI bindings.
    // asObject() returns a new wrapper whose binding to the numeric property is weak:
    // if nobody keeps a reference to the wrapper it is garbage collected and the GUI
    // silently stops updating the parameters. Keeping them as fields prevents that.
    private final ObjectProperty<Double> alphaObject = alpha.asObject();
    private final ObjectProperty<Double> betaObject = beta.asObject();
    private final ObjectProperty<Double> gammaObject = gamma.asObject();
    private final ObjectProperty<Double> kappaObject = kappa.asObject();
    private final ObjectProperty<Double> muObject = mu.asObject();
    private final ObjectProperty<Double> convergenceThresholdObject = convergenceThreshold.asObject();
    private final ObjectProperty<Integer> iterationsObject = iterations.asObject();
    private final ObjectProperty<Integer> gvfIterationsObject = gvfIterations.asObject();

    // Flag to indicate if only internal forces should be used (no GVF/GGVF calculation)
    private final BooleanProperty useInternalForcesOnly = new SimpleBooleanProperty(false);

    private ObjectProperty<ConvergenceMetrics> convergenceMetrics = new SimpleObjectProperty<>(new ConvergenceMetrics());

    Map<String, String> parameterDisplayNames = setupParameterDisplayNames();

    private Map<String, String> setupParameterDisplayNames() {
        Map<String, String> displayNames = new HashMap<String, String>();
        displayNames.put("Elasticity", "alpha");
        displayNames.put("Rigidity", "beta");
        displayNames.put("Viscosity", "gamma");
        displayNames.put("Edge Pull Strength", "kappa");
        displayNames.put("Smoothness Control", "mu");
        displayNames.put("Vector Field Iterations", "gvfIterations");
        displayNames.put("Maximum Iterations", "iterations");
        displayNames.put("Convergence Metric", "convergenceMetric");
        displayNames.put("Convergence Threshold", "convergenceThreshold");
        return displayNames;
    }

    public double getAlpha() {
        return this.alpha.get();
    }
    public Property<Double> alphaProperty() {
        return this.alphaObject;
    }

    public void setAlpha(double value) {
        this.alpha.set(value);
    }

    public double getBeta() {
        return this.beta.get();
    }
    public Property<Double> betaProperty() {
        return this.betaObject;
    }

    public void setBeta(double value) {
        this.beta.set(value);
    }

    public double getGamma() {
        return this.gamma.get();
    }
    public Property<Double> gammaProperty() {
        return this.gammaObject;
    }

    public void setGamma(double value) {
        this.gamma.set(value);
    }

    public double getKappa() {
        return this.kappa.get();
    }
    public Property<Double> kappaProperty() {
        return this.kappaObject;
    }

    public void setKappa(double value) {
        this.kappa.set(value);
    }

    public double getMu() {
        return this.mu.get();
    }
    public Property<Double> muProperty() {
        return this.muObject;
    }

    public void setMu(double value) {
        this.mu.set(value);
    }

    public int getIterations() {
        return this.iterations.get();
    }
    public Property<Integer> iterationsProperty() {
        return this.iterationsObject;
    }

    public void setIterations(int value) {
        this.iterations.set(value);
    }

    public int getGVFIterations() {
        return this.gvfIterations.get();
    }
    public Property<Integer> gvfIterationsProperty() {
        return this.gvfIterationsObject;
    }

    public void setGVFIterations(int value) {
        this.gvfIterations.set(value);
    }

    public boolean getUseInternalForcesOnly() {
        return this.useInternalForcesOnly.get();
    }
    
    public BooleanProperty useInternalForcesOnlyProperty() {
        return this.useInternalForcesOnly;
    }
    
    public void setUseInternalForcesOnly(boolean value) {
        this.useInternalForcesOnly.set(value);
    }

    public ConvergenceMetrics getConvergenceMetrics() {
        return this.convergenceMetrics.get();
    }
    public ObjectProperty<ConvergenceMetrics> convergenceMetricsProperty() {
        return this.convergenceMetrics;
    }

    public void setConvergenceMetrics(ConvergenceMetrics value) {
        this.convergenceMetrics.set(value);
    }

    public ConvergenceMetric getActiveConvergenceMetric() {
        return this.getConvergenceMetrics().getActiveMetric();
    }

    public double getConvergenceThreshold() {
        return this.convergenceThreshold.get();
    }
    public Property<Double> convergenceThresholdProperty() {
        return this.convergenceThresholdObject;
    }

    public void setConvergenceThreshold(double value) {
        this.convergenceThreshold.set(value);
    }

    /**
     * Reads a parameters file written by {@link #saveToFile(File)}.
     * The format is a flat list of "Name" = value lines (a subset of TOML). It is parsed
     * here directly: the TOML library used before was not shipped with the plugin nor
     * with Fiji, so "Load parameters" failed with NoClassDefFoundError.
     * Unknown keys are ignored; missing keys keep their current value.
     */
    public void setFromFile(File parametersFile) throws IOException {
        Map<String, String> values = new HashMap<String, String>();
        for (String rawLine : Files.readAllLines(parametersFile.toPath(), StandardCharsets.UTF_8)) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("[")) continue;
            int separator = line.indexOf('=');
            if (separator < 0) continue;
            String key = unquote(line.substring(0, separator).trim());
            String value = line.substring(separator + 1).trim();
            if (!value.startsWith("\"")) {
                int comment = value.indexOf('#');
                if (comment >= 0) value = value.substring(0, comment).trim();
            }
            values.put(key, unquote(value));
        }

        try {
            mapDouble(values, "Elasticity", this.alpha);
            mapDouble(values, "Rigidity", this.beta);
            mapDouble(values, "Viscosity", this.gamma);
            mapDouble(values, "Edge Pull Strength", this.kappa);
            mapDouble(values, "Smoothness Control", this.mu);
            mapDouble(values, "Convergence Threshold", this.convergenceThreshold);

            mapInteger(values, "Vector Field Iterations", this.gvfIterations);
            mapInteger(values, "Maximum Iterations", this.iterations);
        } catch (NumberFormatException e) {
            throw new IOException("Invalid number in parameters file: " + e.getMessage(), e);
        }

        String metricName = values.get("Convergence Metric");
        if (metricName != null) {
            this.getConvergenceMetrics().setActiveMetric(metricName);
        }
    }

    private static String unquote(String text) {
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

    public void saveToFile(File parametersFile) throws IOException {
        String tomlContent = "# ContourJ Parameters\n\n";
        
        tomlContent += "\"Elasticity\" = " + this.alpha.get() + "\n";
        tomlContent += "\"Rigidity\" = " + this.beta.get() + "\n";
        tomlContent += "\"Viscosity\" = " + this.gamma.get() + "\n";
        tomlContent += "\"Edge Pull Strength\" = " + this.kappa.get() + "\n";
        tomlContent += "\"Smoothness Control\" = " + this.mu.get() + "\n";
        tomlContent += "\"Convergence Threshold\" = " + this.convergenceThreshold.get() + "\n";
        tomlContent += "\"Vector Field Iterations\" = " + this.gvfIterations.get() + "\n";
        tomlContent += "\"Maximum Iterations\" = " + this.iterations.get() + "\n";
        tomlContent += "\"Convergence Metric\" = \"" + this.getConvergenceMetrics().getActiveMetric().getName() + "\"\n";

        try (java.io.FileWriter writer = new java.io.FileWriter(parametersFile)) {
            writer.write(tomlContent);
        }
    }

    private void mapDouble(Map<String, String> values, String key, DoubleProperty property) {
        String value = values.get(key);
        if (value != null) {
            property.set(Double.parseDouble(value));
        }
    }

    private void mapInteger(Map<String, String> values, String key, IntegerProperty property) {
        String value = values.get(key);
        if (value != null) {
            property.set((int) Math.round(Double.parseDouble(value)));
        }
    }

    @Override
    public String toString() {
        String out = "";

        out += "alpha: " + alpha + "\n";
        out += "beta: " + beta + "\n";
        out += "gamma: " + gamma + "\n";
        out += "kappa: " + kappa + "\n";
        out += "mu: " + mu + "\n";
        out += "iterations: " + iterations + "\n";
        out += "gvf_iterations: " + gvfIterations + "\n";
        out += "convergenceMetric: " + getConvergenceMetrics().getActiveMetric() + "\n";
        out += "convergenceLimit: " + convergenceThreshold;

        return out;
    }

}

