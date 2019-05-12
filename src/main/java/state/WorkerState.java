package state;

import messages.AppMessages;

import java.io.Serializable;
import java.util.ArrayList;

public class WorkerState implements Serializable {
    private static final long serialVersionUID = 1L;
    private final ArrayList<AppMessages.JobMessage> jobMessages;

    public WorkerState() {
        this(new ArrayList<>());
    }

    public WorkerState(ArrayList<AppMessages.JobMessage> jobMessages) {
        this.jobMessages = jobMessages;
    }

    public WorkerState copy() {
        return new WorkerState(new ArrayList<>(jobMessages));
    }

    public void update(AppMessages.Event evt) {
        jobMessages.add(evt.getJobMessage());
    }

    public int size() {
        return jobMessages.size();
    }

    @Override
    public String toString() {
        return jobMessages.toString();
    }
}
