package se.kth.chord.timeout;

import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.Timeout;

public class RebuildCheckTimeout extends Timeout {

    public RebuildCheckTimeout(SchedulePeriodicTimeout request) {
        super(request);
    }
}