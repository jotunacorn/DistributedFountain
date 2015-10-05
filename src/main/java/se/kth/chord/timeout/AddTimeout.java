package se.kth.chord.timeout;

import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;

public class AddTimeout extends Timeout {

    public AddTimeout(ScheduleTimeout request) {
        super(request);
    }
}