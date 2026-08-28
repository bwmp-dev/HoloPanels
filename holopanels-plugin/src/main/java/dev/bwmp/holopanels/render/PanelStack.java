package dev.bwmp.holopanels.render;

import java.util.ArrayList;
import java.util.List;

/**
 * How a panel's lines are laid out down its box.
 * <p>
 * A text display draws every line at one size, so lines that want different
 * sizes have to be split across several displays. This works out that split and
 * where each piece sits, measured downward from the top of the box, which is
 * the one place that arithmetic lives.
 */
public final class PanelStack {

    /** Consecutive lines drawn at one scale, and so by one display. */
    public record Run(int firstLine, int lastLine, double scale, double fromTop, double height) {
        public int lines() {
            return lastLine - firstLine + 1;
        }
    }

    /** The vertical slice of the box one line occupies, measured from the top. */
    public record Band(int line, double fromTop, double toTop) {
    }

    private PanelStack() {
    }

    public static List<Run> runs(List<Double> scales, double lineHeight) {
        List<Run> runs = new ArrayList<>();
        double fromTop = 0.0;
        int first = 0;
        while (first < scales.size()) {
            int last = first;
            while (last + 1 < scales.size() && scales.get(last + 1).equals(scales.get(first))) {
                last++;
            }
            double scale = scales.get(first);
            double height = (last - first + 1) * lineHeight * scale;
            runs.add(new Run(first, last, scale, fromTop, height));
            fromTop += height;
            first = last + 1;
        }
        return runs;
    }

    public static double height(List<Run> runs) {
        return runs.stream().mapToDouble(Run::height).sum();
    }

    public static List<Band> bands(List<Run> runs, double lineHeight) {
        List<Band> bands = new ArrayList<>();
        for (Run run : runs) {
            double step = lineHeight * run.scale();
            for (int line = run.firstLine(); line <= run.lastLine(); line++) {
                double top = run.fromTop() + (line - run.firstLine()) * step;
                bands.add(new Band(line, top, top + step));
            }
        }
        return bands;
    }
}
