package cl.scian.contourj.adjustment;

import cl.scian.contourj.event.ImageNotFoundEvent;
import cl.scian.contourj.model.*;
import cl.scian.contourj.model.helpers.convergence.metrics.ConvergenceMetric;
import cl.scian.contourj.model.helpers.MatrixOps;
import cl.scian.contourj.model.helpers.Sampling;
import cl.scian.contourj.model.helpers.interpolation.BilinearInterpolation;

import ij.IJ;
import ij.ImagePlus;
import net.imagej.Dataset;
import net.imagej.display.ImageDisplay;

import org.scijava.convert.ConvertService;
import org.scijava.event.EventService;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.stream.IntStream;

@Plugin(type = ContourAdjuster.class)
public class ActiveContours extends AbstractContourAdjuster {

    private static String NAME = "Active Contours";

    @Parameter
    private LogService log;

    @Parameter
    private ConvertService convertService;

   @Parameter
   private EventService eventService;

    private ImageDisplay sourceDisplay;

    private Contours initialContours;

    private Contours adjustedContours;

    private HashMap<Integer, HashMap<Integer, VectorFlow>> vectorFlowMap = new HashMap<>();

    // Summary of the last run, written to the ImageJ Log window
    private final StringBuilder runReport = new StringBuilder();

    public ActiveContours() {
        super();
        this.setName(NAME);
    }

    @Override
    public void setSourceDisplay(ImageDisplay selectedDisplay) {
        this.sourceDisplay = selectedDisplay;
    }

    @Override
    public Contours getInitialContours () {
        return this.initialContours;
    }

    @Override
    public void setInitialContours(Contours contours) {
        this.initialContours = contours;
    }

    @Override
    public Contours getAdjustedContours () {
        return this.adjustedContours;
    }

    private double alpha() {
        return parameters().getAlpha();
    }

    private double beta() {
        return parameters().getBeta();
    }

    private double gamma() {
        return parameters().getGamma();
    }

    private double kappa() { // TODO: Fix
        // return 0;
        return parameters().getKappa();
    }

    private int iterations() {
        return parameters().getIterations();
    }

    private double convergenceThreshold() {
        return parameters().getConvergenceThreshold();
    }

    private ConvergenceMetric convergenceMetric() {
        return parameters().getConvergenceMetrics().getActiveMetric();
    }

    private VectorFlow getVectorFlow(int frame, int depth) { // TODO: Handle case where no image is given
        if (!this.vectorFlowMap.containsKey(frame)) {
            HashMap<Integer, VectorFlow> depthHashMap = new HashMap<>();
            depthHashMap.put(
                depth,
                new GeneralizedGradientVectorFlow( // TODO: Get parameters in a proper way
                    this.getSourceImageAsFloatArray(), this.parameters().getMu(), this.parameters().getGVFIterations()
                )
            );
            this.vectorFlowMap.put(frame, depthHashMap);
        } else {
            if (!this.vectorFlowMap.get(frame).containsKey(depth)) {
                this.vectorFlowMap.get(frame).put(
                    depth,
                    new GeneralizedGradientVectorFlow( // TODO: Get parameters in a proper way
                            this.getSourceImageAsFloatArray(), this.parameters().getMu(), this.parameters().getGVFIterations()
                    )
                );
            }
        }
        return this.vectorFlowMap.get(frame).get(depth);
    }

    @Override
    public Dataset getDataset() {
        try {
            return (Dataset) sourceDisplay.getActiveView().getData();
        } catch (NullPointerException e) {
            eventService.publish(new ImageNotFoundEvent());
            return null;
        }
    }

    public ImagePlus getImagePlus() {
        return convertService.convert(this.getDataset(), ImagePlus.class);
    }

    public float[][] getSourceImageAsFloatArray() {
        return this.getImagePlus().getProcessor().getFloatArray();
    }

    public Contour arcSample(Contour contour, boolean closeCurve) {
        double[][] sampledPoints = Sampling.arcSample(contour, contour.numberOfPoints(), closeCurve);
        return new Contour(sampledPoints[Contour.xIndex], sampledPoints[Contour.yIndex], contour.getFrame(), contour.getDepth());
    }

    private Contour adjust(Contour contour) {

        int currentFrame = contour.getFrame();
        int currentDepth = contour.getDepth();

        // Only get vector flow if not using internal forces only
        double[][] u = null;
        double[][] v = null;
        
        // Only calculate vector flow if we're using external forces
        if (!parameters().getUseInternalForcesOnly()) {
            u = this.getVectorFlow(currentFrame, currentDepth).getU();
            v = this.getVectorFlow(currentFrame, currentDepth).getV();
        }

        Contour sampledContour = arcSample(contour, true); // TODO: Remove hardcoded closeCurve
        int pointsToIterate = sampledContour.numberOfPoints();

        double[] a = new double[pointsToIterate];
        Arrays.fill(a, this.beta());

        double[] b = new double[pointsToIterate];
        Arrays.fill(b, -this.alpha() - 4*this.beta());

        double[] c = new double[pointsToIterate];
        Arrays.fill(c, 2*this.alpha() + 6*this.beta() + this.gamma());

        double[][] invertedAbcMatrix = MatrixOps.invertMatrix(MatrixOps.makeABCMatrix(a, b, c));

        double initialPerimeter = sampledContour.getClosedPerimeter();
        int iterationsDone = 0;
        double variation = Double.NaN;
        boolean converged = false;

        for (int i = 1; i <= this.iterations(); i++) {

            // Set external forces to zero if using internal forces only
            final double[] vfx;
            final double[] vfy;
            
            // Only apply external forces if not using internal forces only and kappa > 0
            if (!parameters().getUseInternalForcesOnly() && this.kappa() > 0) {
                vfx = BilinearInterpolation.interpolate(v, sampledContour.getX(), sampledContour.getY());
                vfy = BilinearInterpolation.interpolate(u, sampledContour.getX(), sampledContour.getY());
            } else {
                vfx = new double[sampledContour.getX().length];
                vfy = new double[sampledContour.getY().length];
            }

            double[] adjustedX = MatrixOps.matrixMultiplication(invertedAbcMatrix,
                    IntStream.range(0, sampledContour.getX().length)
                            .mapToDouble(elem -> this.kappa() > 0 && !parameters().getUseInternalForcesOnly() ?
                                    this.gamma() * sampledContour.getX()[elem] + this.kappa() * vfx[elem] :
                                    this.gamma() * sampledContour.getX()[elem])
                            .toArray()
            );

            double[] adjustedY = MatrixOps.matrixMultiplication(invertedAbcMatrix,
                    IntStream.range(0, sampledContour.getY().length)
                            .mapToDouble(elem -> this.kappa() > 0 && !parameters().getUseInternalForcesOnly() ?
                                    this.gamma() * sampledContour.getY()[elem] + this.kappa() * vfy[elem] :
                                    this.gamma() * sampledContour.getY()[elem])
                            .toArray()
            );

            variation = this.convergenceMetric().calculate(
                    sampledContour,
                    new Contour(adjustedX, adjustedY, sampledContour.getFrame(), sampledContour.getDepth())
            );

            sampledContour.update(adjustedX, adjustedY);

            log.debug("Iter: " + i + " | Var: " + variation);

            iterationsDone = i;

            if (variation < convergenceThreshold()) {
                converged = true;
                break;
            }

        }

        runReport.append(String.format(java.util.Locale.US,
                "  points: %d | iterations: %d of %d | stop: %s | last variation: %.5f | perimeter: %.3f -> %.3f%n",
                pointsToIterate, iterationsDone, this.iterations(),
                converged ? "converged (variation < threshold)" : "maximum iterations reached",
                variation, initialPerimeter, sampledContour.getClosedPerimeter()));

        return sampledContour;
    }

    public void runAdjustment() {

        this.adjustedContours = new Contours(this.initialContours.size());

        // The vector field depends on the image, mu and the number of field iterations.
        // It used to be cached for the whole life of the window and was never recomputed,
        // so a change of image or of those parameters had no effect until the plugin was
        // reopened. It is now cached only within a run (shared by contours on the same plane).
        this.vectorFlowMap.clear();

        runReport.setLength(0);
        runReport.append("ContourJ run, ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())).append("\n");
        boolean internalOnly = parameters().getUseInternalForcesOnly();
        String imageTitle = "-";
        if (!internalOnly) {
            ImagePlus imp = this.getImagePlus();
            if (imp != null) imageTitle = imp.getTitle();
        }
        runReport.append("  image: ").append(imageTitle)
                .append(" | forces: ").append(internalOnly ? "internal only" : "internal and external").append("\n");
        runReport.append(String.format(java.util.Locale.US,
                "  parameters: elasticity=%s rigidity=%s viscosity=%s edge pull=%s smoothness=%s "
                        + "field iterations=%d max iterations=%d metric=%s threshold=%s%n",
                alpha(), beta(), gamma(), kappa(), parameters().getMu(),
                parameters().getGVFIterations(), iterations(), convergenceMetric().getName(), convergenceThreshold()));

        for (int i = 0; i < initialContours.size(); i++) {

            Contour currentContour = initialContours.get(i).copy();

            runReport.append("ROI ").append(i + 1).append(" of ").append(initialContours.size()).append("\n");

            Contour adjustedContour = adjust(currentContour);

            this.adjustedContours.add(adjustedContour);
        }

        IJ.log(runReport.toString());
    }

    @Override
    public String info(){
        String out = "";

        out += "Name:" + this.getName() + "\n";
        out += "N Initial Contours: " + this.initialContours.size() + "\n";
        out += this.parameters() + "\n";

        return out;
    }

}
