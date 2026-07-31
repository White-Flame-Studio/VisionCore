package com.xnexusacs.visioncore.common.subtitles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class SubtitleTrack {

    private final String label;
    private final List<SubtitleCue> cues;

    public SubtitleTrack(String label, List<SubtitleCue> cues) {
        this.label = label;
        List<SubtitleCue> sorted = new ArrayList<>(cues);
        sorted.sort(Comparator.comparingLong(SubtitleCue::startMillis));
        this.cues = Collections.unmodifiableList(sorted);
    }

    public String label() {
        return label;
    }

    public List<SubtitleCue> cues() {
        return cues;
    }

    public int size() {
        return cues.size();
    }

    public SubtitleCue cueAt(long timeMillis) {
        int lo = 0;
        int hi = cues.size() - 1;

        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            SubtitleCue candidate = cues.get(mid);

            if (timeMillis < candidate.startMillis()) {
                hi = mid - 1;
            } else if (timeMillis >= candidate.endMillis()) {
                lo = mid + 1;
            } else {
                return candidate;
            }
        }

        return null;
    }
}
