package dev.bwmp.holopanels.render;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PanelStackTest {

    @Test
    void linesThatAgreeOnAScaleShareOneRun() {
        List<PanelStack.Run> runs = PanelStack.runs(List.of(1.0, 1.0, 1.0), 0.25);

        assertEquals(1, runs.size());
        assertEquals(0, runs.get(0).firstLine());
        assertEquals(2, runs.get(0).lastLine());
        assertEquals(0.75, runs.get(0).height());
        assertEquals(0.75, PanelStack.height(runs));
    }

    @Test
    void aLineAtAnotherScaleSplitsTheStack() {
        List<PanelStack.Run> runs = PanelStack.runs(List.of(1.0, 2.4, 1.2, 1.2), 0.25);

        assertEquals(3, runs.size());
        assertEquals(List.of(0, 1, 2), runs.stream().map(PanelStack.Run::firstLine).toList());
        assertEquals(List.of(0, 1, 3), runs.stream().map(PanelStack.Run::lastLine).toList());
        assertEquals(2, runs.get(2).lines());
    }

    @Test
    void runsStackDownwardsFromTheTop() {
        List<PanelStack.Run> runs = PanelStack.runs(List.of(1.0, 2.0, 1.0, 1.0), 0.25);

        assertEquals(0.0, runs.get(0).fromTop());
        assertEquals(0.25, runs.get(1).fromTop());
        assertEquals(0.75, runs.get(2).fromTop());
        assertEquals(0.25, runs.get(0).height());
        assertEquals(0.5, runs.get(1).height());
        assertEquals(0.5, runs.get(2).height());
        assertEquals(1.25, PanelStack.height(runs));
    }

    @Test
    void everyLineGetsABandAtItsOwnHeight() {
        List<PanelStack.Band> bands = PanelStack.bands(PanelStack.runs(List.of(1.0, 2.0, 2.0), 0.25), 0.25);

        assertEquals(3, bands.size());
        assertEquals(new PanelStack.Band(0, 0.0, 0.25), bands.get(0));
        assertEquals(new PanelStack.Band(1, 0.25, 0.75), bands.get(1));
        assertEquals(new PanelStack.Band(2, 0.75, 1.25), bands.get(2));
    }

    @Test
    void noLinesIsNoRuns() {
        assertEquals(List.of(), PanelStack.runs(List.of(), 0.25));
        assertEquals(0.0, PanelStack.height(List.of()));
    }
}
