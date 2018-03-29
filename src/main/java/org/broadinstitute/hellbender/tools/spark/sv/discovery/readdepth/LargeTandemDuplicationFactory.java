package org.broadinstitute.hellbender.tools.spark.sv.discovery.readdepth;

import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.util.OverlapDetector;
import org.broadinstitute.hellbender.tools.copynumber.formats.records.CalledCopyRatioSegment;
import org.broadinstitute.hellbender.tools.copynumber.formats.records.CopyRatio;
import org.broadinstitute.hellbender.tools.spark.sv.StructuralVariationDiscoveryArgumentCollection;
import org.broadinstitute.hellbender.tools.spark.sv.utils.IntrachromosomalBreakpointPair;
import org.broadinstitute.hellbender.tools.spark.sv.discovery.SimpleSVType;
import org.broadinstitute.hellbender.tools.spark.sv.utils.SVIntervalUtils;
import org.broadinstitute.hellbender.tools.spark.sv.discovery.inference.LargeSimpleSV;
import org.broadinstitute.hellbender.tools.spark.sv.evidence.EvidenceTargetLink;
import org.broadinstitute.hellbender.tools.spark.sv.utils.PairedStrandedIntervals;
import org.broadinstitute.hellbender.tools.spark.sv.utils.SVInterval;
import org.broadinstitute.hellbender.tools.spark.sv.utils.SVIntervalTree;
import org.broadinstitute.hellbender.utils.read.GATKRead;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Calls large tandem duplication variants
 */
public class LargeTandemDuplicationFactory extends LargeSimpleSVFactory {

    public LargeTandemDuplicationFactory(final SVIntervalTree<EvidenceTargetLink> intrachromosomalLinkTree,
                                         final SVIntervalTree<EvidenceTargetLink> interchromosomalLinkTree,
                                         final SVIntervalTree<GATKRead> contigTree,
                                         final StructuralVariationDiscoveryArgumentCollection.DiscoverVariantsFromReadDepth arguments,
                                         final OverlapDetector<CalledCopyRatioSegment> copyRatioSegmentOverlapDetector,
                                         final OverlapDetector<CopyRatio> readDepthOverlapDetector,
                                         final SAMSequenceDictionary dictionary) {
        super(intrachromosomalLinkTree, interchromosomalLinkTree, contigTree, arguments, copyRatioSegmentOverlapDetector, readDepthOverlapDetector, dictionary);
    }

    @Override
    protected LargeSimpleSV getNewSV(final int start,
                                     final int end,
                                     final int contigId,
                                     final String contig,
                                     final int readPairEvidence,
                                     final int splitReadEvidence,
                                     final int readPairCounterEvidence,
                                     final int splitReadCounterEvidence,
                                     final List<CopyRatio> coverage,
                                     final List<Integer> copyNumberStates,
                                     final IntrachromosomalBreakpointPair breakpoints) {
        return new LargeSimpleSV(SimpleSVType.TYPES.DUP, start, end, contigId, readPairEvidence, splitReadEvidence, readPairCounterEvidence, splitReadCounterEvidence, breakpoints);
    }

    /**
     * Checks if the link matches "outie" read pair orientation, i.e. -/+
     */
    @Override
    protected boolean isEvidenceOrientation(final EvidenceTargetLink link) {
        final PairedStrandedIntervals intervals = link.getPairedStrandedIntervals();
        return !intervals.getLeft().getStrand() && intervals.getRight().getStrand();
    }

    /**
     * Returns elevated copy number states (3, 4, ...)
     */
    @Override
    protected Set<Integer> getValidHMMCopyStates(final int numStates) {
        return IntStream.range(3, numStates).boxed().collect(Collectors.toSet());
    }

    /**
     * Tests if an amplification call matches the interval
     */
    @Override
    protected boolean supportedBySegmentCalls(final SVInterval interval, final Set<CalledCopyRatioSegment> overlappingSegments, final SAMSequenceDictionary dictionary) {
        final int amplifiedBases = overlappingSegments.stream().filter(segment -> segment.getCall() == CalledCopyRatioSegment.Call.AMPLIFICATION)
                //.filter(segment -> IntervalUtils.percentOverlap(segment.getInterval(), interval, dictionary) >= arguments.DEFAULT_MIN_SEGMENT_OVERLAP)
                .mapToInt(segment -> SVIntervalUtils.convertInterval(segment.getInterval(), dictionary).overlapLen(interval)).sum();
        return amplifiedBases / (double) interval.getLength() >= arguments.minSegmentOverlap;
    }

    /**
     * Tests if there is a suspicious number of copy ratio bins that are too high or too low
     */
    @Override
    protected boolean isInvalidCoverage(final List<CopyRatio> copyRatios) {
        if (!(copyRatios.stream().filter(ratio -> ratio.getLog2CopyRatioValue() < arguments.tandemDuplicationInvalidLog2CopyRatioThreshold).count() > arguments.tandemDuplicationInvalidBinFraction * copyRatios.size())) {
            return false;
        }
        return copyRatios.stream().anyMatch(ratio -> Math.pow(2.0, ratio.getLog2CopyRatioValue())  >= arguments.hmmMaxStates);
    }
}